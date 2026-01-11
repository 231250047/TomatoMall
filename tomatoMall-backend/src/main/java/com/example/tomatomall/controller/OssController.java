package com.example.tomatomall.controller;

import com.example.tomatomall.Util.ResultUtil;
import com.example.tomatomall.service.OssService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/oss/fileoss")
public class OssController {

    @Autowired
    private OssService ossService;

    @PostMapping
    public ResultUtil uploadOssFile(@RequestParam MultipartFile file){

        /**返回上传到oss的路径*/
        String url = ossService.uploadFileAvatar(file);

        return ResultUtil.success(url);
    }
}
