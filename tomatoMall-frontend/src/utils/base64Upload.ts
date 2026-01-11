import { ElMessage } from 'element-plus'

/**
 * 将图片文件转换为 Base64 字符串
 * @param file 图片文件
 * @returns Promise<string> Base64 字符串
 */
export const fileToBase64 = (file: File): Promise<string> => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()

    reader.onload = (e) => {
      const base64String = e.target?.result as string
      resolve(base64String)
    }

    reader.onerror = (error) => {
      reject(error)
    }

    reader.readAsDataURL(file)
  })
}

/**
 * 压缩图片并转换为 Base64
 * @param file 原始图片文件
 * @param maxWidth 最大宽度（默认 400 - 减小尺寸以减少 Base64 大小）
 * @param quality 压缩质量 0-1（默认 0.7 - 降低质量以减少 Base64 大小）
 * @returns Promise<string> 压缩后的 Base64 字符串
 */
export const compressImageToBase64 = (
  file: File,
  maxWidth: number = 400,  // 改为 400px
  quality: number = 0.7     // 改为 0.7
): Promise<string> => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()

    reader.onload = (e) => {
      const img = new Image()
      img.src = e.target?.result as string

      img.onload = () => {
        // 创建 canvas 进行压缩
        const canvas = document.createElement('canvas')
        const ctx = canvas.getContext('2d')

        if (!ctx) {
          reject(new Error('无法获取 Canvas 上下文'))
          return
        }

        // 计算压缩后的尺寸
        let width = img.width
        let height = img.height

        if (width > maxWidth) {
          height = (maxWidth / width) * height
          width = maxWidth
        }

        canvas.width = width
        canvas.height = height

        // 绘制并压缩
        ctx.drawImage(img, 0, 0, width, height)

        // 转换为 Base64
        const compressedBase64 = canvas.toDataURL('image/jpeg', quality)
        resolve(compressedBase64)
      }

      img.onerror = () => {
        reject(new Error('图片加载失败'))
      }
    }

    reader.onerror = (error) => {
      reject(error)
    }

    reader.readAsDataURL(file)
  })
}

/**
 * 模拟 OSS 上传（实际存储为 Base64）
 * 用于替代阿里云 OSS，避免欠费问题
 */
export const uploadFileAsBase64 = async (formData: FormData): Promise<any> => {
  try {
    const file = formData.get('file') as File

    if (!file) {
      throw new Error('未找到文件')
    }

    console.log(`开始处理图片，原始大小: ${(file.size / 1024).toFixed(2)} KB`);

    // 所有图片都压缩（头像不需要太大）
    // 限制最大宽度 400px，质量 0.6，确保 Base64 不会太大
    const compressedBase64 = await compressImageToBase64(file, 400, 0.6)

    const base64SizeKB = (compressedBase64.length * 0.75 / 1024).toFixed(2);
    console.log(`压缩后 Base64 大小: ${base64SizeKB} KB`);

    return {
      data: {
        code: '200',
        data: compressedBase64,
        message: `上传成功（已压缩至 ${base64SizeKB} KB）`
      }
    }
  } catch (error) {
    console.error('Base64 转换失败:', error)
    return {
      data: {
        code: '500',
        message: '上传失败'
      }
    }
  }
}
