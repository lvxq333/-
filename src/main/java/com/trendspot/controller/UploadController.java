package com.trendspot.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.trendspot.dto.Result;
import com.trendspot.utils.AliOssUtil;
import com.trendspot.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("upload")
public class UploadController {

    @Autowired
    private AliOssUtil aliOssUtil;

    /**
     * 文件上传_本地，需要修改blog-edit.html中第127行为
     * .then(({data}) => this.fileList.push(data))
     *
     * @param image
     * @return
     */
    @PostMapping("blog")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        log.info("上传图片：{}", image);
        try {
            // 原文件名
            String originalFilename = image.getOriginalFilename();
            // 截取原始文件名后缀
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            // 构造新文件名称，为了避免当用户上传的名称同名时，后上传的会覆盖先上传的
            String uuidName = UUID.randomUUID().toString() + extension;
            //
            String filePath = aliOssUtil.upload(image.getBytes(), uuidName);
            log.debug("文件上传成功，{}", filePath);
            return Result.ok(filePath);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

//    /**
//     * 文件上传_本地，需要修改blog-edit.html中第127行为
//     * .then(({data}) => this.fileList.push('/imgs' + data))
//     * @param image
//     * @return
//     */
//    @PostMapping("blog")
//    public Result uploadImage(@RequestParam("file") MultipartFile image) {
//        log.info("上传图片：{}", image);
//        try {
//            // 获取原始文件名称
//            String originalFilename = image.getOriginalFilename();
//            // 生成新文件名
//            String fileName = createNewFileName(originalFilename);
//            // 保存文件
//            image.transferTo(new File(SystemConstants.IMAGE_UPLOAD_DIR, fileName));
//            // 返回结果
//            log.debug("文件上传成功，{}", fileName);
//            return Result.ok(fileName);
//        } catch (IOException e) {
//            throw new RuntimeException("文件上传失败", e);
//        }
//    }

    @GetMapping("/blog/delete")
    public Result deleteBlogImg(@RequestParam("name") String filename) {
        File file = new File(SystemConstants.IMAGE_UPLOAD_DIR, filename);
        if (file.isDirectory()) {
            return Result.fail("错误的文件名称");
        }
        FileUtil.del(file);
        return Result.ok();
    }

    private String createNewFileName(String originalFilename) {
        // 获取后缀
        String suffix = StrUtil.subAfter(originalFilename, ".", true);
        // 生成目录
        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        // 判断目录是否存在
        File dir = new File(SystemConstants.IMAGE_UPLOAD_DIR, StrUtil.format("/blogs/{}/{}", d1, d2));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 生成文件名
        return StrUtil.format("/blogs/{}/{}/{}.{}", d1, d2, name, suffix);
    }
}
