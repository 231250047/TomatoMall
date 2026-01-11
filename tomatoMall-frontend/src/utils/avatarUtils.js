/**
 * 生成本地 SVG 头像（避免 CORS 问题）
 * @param {string} name - 显示的名称
 * @param {number} size - 头像大小
 * @param {string} bgColor - 背景颜色（十六进制，不带#）
 * @returns {string} Data URL 格式的 SVG 头像
 */
export function generateLocalAvatar(name = '?', size = 48, bgColor = '3b82f6') {
  // 获取名称的首字符
  const initial = name.charAt(0).toUpperCase();
  
  // 生成 SVG
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
    <rect fill="#${bgColor}" width="${size}" height="${size}" rx="${size / 8}"/>
    <text fill="#ffffff" font-family="Arial, sans-serif" font-size="${size / 2}" font-weight="bold" x="50%" y="50%" text-anchor="middle" dy="0.35em">${initial}</text>
  </svg>`;
  
  // 转换为 Data URL
  return `data:image/svg+xml,${encodeURIComponent(svg)}`;
}

/**
 * 根据用户ID生成头像
 * @param {number|string|null} userId - 用户ID
 * @param {string} userName - 用户名（可选）
 * @param {number} size - 头像大小
 * @returns {string} 头像 URL
 */
export function generateUserAvatar(userId, userName = null, size = 48) {
  const isOldProduct = userId === null || userId === 'old';
  const name = isOldProduct ? '旧' : (userName || `U${userId}`);
  const bgColor = isOldProduct ? 'cccccc' : '3b82f6';
  
  return generateLocalAvatar(name, size, bgColor);
}

/**
 * 为 img 标签提供的错误回退函数
 * @param {Event} e - 错误事件
 * @param {string} fallbackName - 回退显示的名称
 * @param {number} size - 头像大小
 */
export function handleAvatarError(e, fallbackName = '?', size = 48) {
  e.target.src = generateLocalAvatar(fallbackName, size, '999999');
}
