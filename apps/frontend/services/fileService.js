import axios, { isCancel, CancelToken } from 'axios';
import axiosInstance from './axios';
import { Toast } from '../components/Toast';

class FileService {
  constructor() {
    this.baseUrl = process.env.NEXT_PUBLIC_API_URL;
    this.uploadLimit = 50 * 1024 * 1024; // 50MB
    this.retryAttempts = 3;
    this.retryDelay = 1000;
    this.activeUploads = new Map();

    this.allowedTypes = {
      image: {
        extensions: ['.jpg', '.jpeg', '.png', '.gif', '.webp'],
        mimeTypes: ['image/jpeg', 'image/png', 'image/gif', 'image/webp'],
        maxSize: 10 * 1024 * 1024,
        name: '이미지',
      },
      document: {
        extensions: ['.pdf'],
        mimeTypes: ['application/pdf'],
        maxSize: 20 * 1024 * 1024,
        name: 'PDF 문서',
      },
    };
  }

  async validateFile(file) {
    if (!file) {
      const message = '파일이 선택되지 않았습니다.';
      Toast.error(message);
      return { success: false, message };
    }

    if (file.size > this.uploadLimit) {
      const message = `파일 크기는 ${this.formatFileSize(this.uploadLimit)}를 초과할 수 없습니다.`;
      Toast.error(message);
      return { success: false, message };
    }

    let isAllowedType = false;
    let maxTypeSize = 0;
    let typeConfig = null;

    for (const config of Object.values(this.allowedTypes)) {
      if (config.mimeTypes.includes(file.type)) {
        isAllowedType = true;
        maxTypeSize = config.maxSize;
        typeConfig = config;
        break;
      }
    }

    if (!isAllowedType) {
      const message = '지원하지 않는 파일 형식입니다.';
      Toast.error(message);
      return { success: false, message };
    }

    if (file.size > maxTypeSize) {
      const message = `${typeConfig.name} 파일은 ${this.formatFileSize(maxTypeSize)}를 초과할 수 없습니다.`;
      Toast.error(message);
      return { success: false, message };
    }

    const ext = this.getFileExtension(file.name);
    if (!typeConfig.extensions.includes(ext.toLowerCase())) {
      const message = '파일 확장자가 올바르지 않습니다.';
      Toast.error(message);
      return { success: false, message };
    }

    return { success: true };
  }

  async uploadFile(file, onProgress, token, sessionId) {
    const validationResult = await this.validateFile(file);
    if (!validationResult.success) {
      return validationResult;
    }

    const source = CancelToken.source();
    this.activeUploads.set(file.name, source);

    const uploadApiUrl = this.baseUrl
      ? `${this.baseUrl}/api/files/upload`
      : '/api/files/upload';

    const completeApiUrl = this.baseUrl
      ? `${this.baseUrl}/api/files/upload/complete`
      : '/api/files/upload/complete';

    try {
      let response;

      try {
        // 1) API에 파일 자체가 아닌 메타데이터만 JSON으로 전송
        // 기존 E2E가 기다리는 /api/files/upload 경로는 그대로 유지된다.
        const prepareResponse = await axiosInstance.post(
          uploadApiUrl,
          {
            filename: file.name,
            contentType: file.type,
            size: file.size,
          },
          {
            timeout: 5000,
            cancelToken: source.token,
          }
        );

        const {
          directUpload,
          uploadUrl: s3UploadUrl,
          file: uploadedFile,
        } = prepareResponse.data || {};

        if (!directUpload || !s3UploadUrl || !uploadedFile?._id) {
          throw new Error('직접 업로드 URL을 받지 못했습니다.');
        }

        // 2) 파일 바이트는 API EC2가 아닌 S3로 직접 전송
        // axiosInstance가 아닌 axios를 사용해야 API 인증 헤더/baseURL이 S3에 붙지 않는다.
        await axios.put(s3UploadUrl, file, {
          headers: {
            'Content-Type': file.type,
          },
          withCredentials: false,
          timeout: 30000,
          cancelToken: source.token,
          onUploadProgress: (event) => {
            if (!onProgress || !event.total) return;

            onProgress(Math.round((event.loaded * 100) / event.total));
          },
        });

        // 3) S3 PUT 완료를 API가 확인하고 기존과 같은 file 응답을 반환
        response = await axiosInstance.post(
          completeApiUrl,
          { fileId: uploadedFile._id },
          {
            timeout: 5000,
            cancelToken: source.token,
          }
        );
      } catch (error) {
        // local 스토리지처럼 직접 업로드 미지원이면 기존 multipart 경로로 폴백
        if (error.response?.status !== 409) {
          throw error;
        }

        const formData = new FormData();
        formData.append('file', file);

        response = await axiosInstance.post(uploadApiUrl, formData, {
          headers: {
            'Content-Type': 'multipart/form-data',
          },
          timeout: 30000,
          cancelToken: source.token,
          withCredentials: true,
          onUploadProgress: (event) => {
            if (!onProgress || !event.total) return;

            onProgress(Math.round((event.loaded * 100) / event.total));
          },
        });
      }

      this.activeUploads.delete(file.name);

      if (!response.data?.success) {
        return {
          success: false,
          message: response.data?.message || '파일 업로드에 실패했습니다.',
        };
      }

      // 기존 useChatFileUpload / useMessageHandling이 기대하는 반환 형태를 그대로 유지
      const fileData = response.data.file;

      return {
        success: true,
        data: {
          ...response.data,
          file: {
            ...fileData,
            url: this.getFileUrl(fileData.filename, true),
          },
        },
      };
    } catch (error) {
      this.activeUploads.delete(file.name);

      if (isCancel(error)) {
        return {
          success: false,
          message: '업로드가 취소되었습니다.',
        };
      }

      if (error.response?.status === 401) {
        throw new Error('Authentication expired. Please login again.');
      }

      return this.handleUploadError(error);
    }
  }
  getFileUrl(filename, forPreview = false) {
    if (!filename) return '';

    const baseUrl = process.env.NEXT_PUBLIC_API_URL || '';
    const endpoint = forPreview ? 'view' : 'download';
    return `${baseUrl}/api/files/${endpoint}/${filename}`;
  }

  getPreviewUrl(file, token, sessionId, withAuth = true) {
    if (!file?.filename) return '';

    const baseUrl = `${process.env.NEXT_PUBLIC_API_URL}/api/files/view/${file.filename}`;

    if (!withAuth) return baseUrl;

    if (!token || !sessionId) return baseUrl;

    // URL 객체 생성 전 프로토콜 확인
    const url = new URL(baseUrl);
    url.searchParams.append('token', encodeURIComponent(token));
    url.searchParams.append('sessionId', encodeURIComponent(sessionId));

    return url.toString();
  }

  getFileExtension(filename) {
    if (!filename) return '';
    const parts = filename.split('.');
    return parts.length > 1 ? `.${parts.pop().toLowerCase()}` : '';
  }

  formatFileSize(bytes) {
    if (!bytes || bytes === 0) return '0 B';
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return `${parseFloat((bytes / Math.pow(1024, i)).toFixed(2))} ${units[i]}`;
  }

  handleUploadError(error) {
    if (error.code === 'ECONNABORTED') {
      return {
        success: false,
        message: '파일 업로드 시간이 초과되었습니다.',
      };
    }

    const status = error.response?.status ?? error.status;
    const message = error.response?.data?.message ?? error.message;

    switch (status) {
      case 400:
        return {
          success: false,
          message: message || '잘못된 요청입니다.',
        };
      case 401:
        return {
          success: false,
          message: '인증이 필요합니다.',
        };
      case 413:
        return {
          success: false,
          message: message || '파일이 너무 큽니다.',
        };
      case 415:
        return {
          success: false,
          message: '지원하지 않는 파일 형식입니다.',
        };
      default:
        break;
    }

    console.error('Upload error:', error);

    if (axios.isAxiosError(error)) {
      switch (status) {
        case 500:
          return {
            success: false,
            message: '서버 오류가 발생했습니다.',
          };
        default:
          return {
            success: false,
            message: message || '파일 업로드에 실패했습니다.',
          };
      }
    }

    return {
      success: false,
      message: error.message || '알 수 없는 오류가 발생했습니다.',
      error,
    };
  }

  cancelUpload(filename) {
    const source = this.activeUploads.get(filename);
    if (source) {
      source.cancel('Upload canceled by user');
      this.activeUploads.delete(filename);
      return {
        success: true,
        message: '업로드가 취소되었습니다.',
      };
    }
    return {
      success: false,
      message: '취소할 업로드를 찾을 수 없습니다.',
    };
  }
}

export default new FileService();
