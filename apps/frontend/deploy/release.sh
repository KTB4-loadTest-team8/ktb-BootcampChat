#!/bin/bash
# EC2 에서 실행되는 릴리스 교체 스크립트.
#
# tarball 을 새 release 디렉터리에 풀고 current 심볼릭 링크를 원자적으로 갈아끼운 뒤
# restart.sh 로 재시작·헬스체크한다. 헬스체크가 실패하면 이전 릴리스로 되돌린다.
#
# 사용법: APP_ROOT=/path/to/app bash release.sh <git-sha>

set -euo pipefail

APP_ROOT="${APP_ROOT:-/home/ubuntu/ktb-chat-frontend}"
KEEP_RELEASES="${KEEP_RELEASES:-5}"
SHA="${1:?사용법: release.sh <git-sha>}"

TARBALL="$APP_ROOT/tmp/$SHA.tar.gz"
RELEASE="$APP_ROOT/releases/$SHA"
CURRENT="$APP_ROOT/current"
RESTART="$APP_ROOT/bin/restart.sh"

[ -f "$TARBALL" ] || { echo "❌ $TARBALL 없음 — 업로드가 안 됐습니다"; exit 1; }
[ -f "$RESTART" ] || { echo "❌ $RESTART 없음 — 업로드가 안 됐습니다"; exit 1; }

# 되돌릴 대상. current 가 아직 없으면(첫 배포) 빈 값이 된다.
PREVIOUS="$(readlink "$CURRENT" 2>/dev/null || true)"

echo "📦 Unpacking $SHA..."
rm -rf "$RELEASE"
mkdir -p "$RELEASE"
tar -xzf "$TARBALL" -C "$RELEASE"

if [ ! -f "$RELEASE/apps/frontend/server.js" ]; then
    echo "❌ server.js 가 tarball 안에 없습니다 — 빌드 산출물을 확인하세요"
    rm -rf "$RELEASE"
    exit 1
fi

# ln -sfn 만 쓰면 unlink 와 symlink 사이에 current 가 없는 순간이 생긴다.
# 임시 링크를 만들고 mv -T 로 rename(2) 하면 그 틈이 사라진다.
switch_to() {
    ln -sfn "$1" "$CURRENT.tmp"
    mv -Tf "$CURRENT.tmp" "$CURRENT"
}

# restart.sh 는 server.js 를 상대 경로로 찾으므로 릴리스 디렉터리에서 실행해야 한다.
# systemd 유닛의 WorkingDirectory 도 current 를 가리키고, systemd 가 시작 시점에
# 심볼릭 링크를 해석하므로 재시작만으로 새 릴리스를 집는다.
restart() {
    ( cd "$CURRENT" && bash "$RESTART" )
}

echo "🔗 Switching current -> releases/$SHA"
switch_to "$RELEASE"

if restart; then
    echo "🧹 Pruning old releases (keeping $KEEP_RELEASES)..."
    ls -1dt "$APP_ROOT"/releases/*/ 2>/dev/null \
        | tail -n "+$((KEEP_RELEASES + 1))" \
        | xargs -r rm -rf
    rm -f "$TARBALL"
    echo "✅ Released $SHA"
    exit 0
fi

echo "❌ Health check failed — rolling back"

if [ -z "$PREVIOUS" ] || [ ! -d "$PREVIOUS" ] || [ "$PREVIOUS" = "$RELEASE" ]; then
    echo "🚨 되돌릴 이전 릴리스가 없습니다 — 수동 개입 필요"
    echo "📋 journalctl -u ktb-frontend.service -n 50 --no-pager"
    exit 1
fi

switch_to "$PREVIOUS"
if restart; then
    echo "↩️  Rolled back to $(basename "$PREVIOUS")"
else
    echo "🚨 롤백마저 실패했습니다 — 수동 개입 필요"
fi
exit 1
