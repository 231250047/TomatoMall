package com.example.tomatomall.service.serviceImpl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.example.tomatomall.Util.ConstantPropertiesUtil;
import com.example.tomatomall.service.OssService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Service
public class OssServiceImpl implements OssService {

    @Override
    public String uploadFileAvatar(MultipartFile multipartFile) {

        String endpoint = ConstantPropertiesUtil.END_POINT;
        String accessKeyId = ConstantPropertiesUtil.KEY_ID;
        String accessKeySecret = ConstantPropertiesUtil.KEY_SECRET;
        // 填写Bucket名称
        String bucketName = ConstantPropertiesUtil.BUCKET_NAME;
        /** 创建OSSClient实例。 */
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {

            /** 获取上传文件输入流 */
            InputStream inputStream = multipartFile.getInputStream();

            /** 获取文件名称 */
            String filename = multipartFile.getOriginalFilename();

            /** 调用oss方法实现上传 */
            // 第一个参数 Bucket名称
            // 第二个参数 上传到oss文件路径或文件名称
            // 第三个参数 上传文件输入流
            ossClient.putObject(bucketName, filename, inputStream);

            // 打印日志，方便调试
            System.out.println("文件上传成功: " + filename);

            /** 返回上传到阿里OSS的路径 */
            String url = "https://".concat(bucketName).concat(".").concat(endpoint).concat("/").concat(filename);
            System.out.println("生成的文件URL: " + url);
            System.out.println("提示：如果图片无法显示，请检查OSS Bucket权限是否设置为【公共读】，并配置CORS跨域规则");

            return url;
        } catch (Exception e) {
            System.err.println("文件上传失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }
}
