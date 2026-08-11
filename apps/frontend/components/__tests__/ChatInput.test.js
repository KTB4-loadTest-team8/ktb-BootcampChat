import React from 'react';
import { render, waitFor, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import ChatInput from '../ChatInput';

vi.mock('../EmojiPicker', () => ({
  default: () => React.createElement('em-emoji-picker'),
}));

describe('ChatInput', () => {
  it('does not submit Enter while a Korean IME composition is active', () => {
    const onSubmit = vi.fn();
    const { getByTestId } = render(
      <ChatInput
        onSubmit={onSubmit}
        fileInputRef={{ current: null }}
        room={{ participants: [] }}
      />
    );
    const input = getByTestId('chat-message-input');

    fireEvent.compositionStart(input);
    fireEvent.change(input, { target: { value: '안녕' } });
    fireEvent.keyDown(input, {
      key: 'Enter',
      code: 'Enter',
      keyCode: 229,
      isComposing: true,
    });

    expect(onSubmit).not.toHaveBeenCalled();
    expect(input).toHaveValue('안녕');

    fireEvent.compositionEnd(input, { data: '녕' });
    fireEvent.keyDown(input, { key: 'Enter', code: 'Enter' });

    expect(onSubmit).toHaveBeenCalledTimes(1);
    expect(onSubmit).toHaveBeenCalledWith({ type: 'text', content: '안녕' });
    expect(input).toHaveValue('');
  });

  it('renders the lazy emoji picker under React 19', async () => {
    const { container, getByLabelText } = render(
      <ChatInput
        fileInputRef={{ current: null }}
        room={{ participants: [] }}
      />
    );

    fireEvent.click(getByLabelText('이모티콘'));

    await waitFor(() => {
      expect(container.querySelector('em-emoji-picker')).toBeInTheDocument();
    });
  });
});
