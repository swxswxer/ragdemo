package com.swx.ragdemo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.swx.ragdemo.service.MyAppDocumentLoader;

import java.util.List;

@Tag(name = "文件接口")
@Slf4j
@RestController
@RequestMapping("/file")
public class FileUploadController {

    private final MyAppDocumentLoader documentLoader;
    private final VectorStore vectorStore;


    public FileUploadController(MyAppDocumentLoader documentLoader, VectorStore vectorStore) {
        this.documentLoader = documentLoader;
        this.vectorStore = vectorStore;
    }

    @Operation(summary = "上传知识库")
    @PostMapping("/upload")
    public ResponseEntity<String> uploadMarkdownFile(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("请选择要上传的文件");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".md")) {
            return ResponseEntity.badRequest().body("只支持上传Markdown文件");
        }

        try {
            List<Document> documents = documentLoader.processUploadedFile(file);
            vectorStore.add(documents);
            return ResponseEntity.ok("文件上传并处理成功");
        } catch (Exception e) {
//            log.error("文件上传失败", e);
            return ResponseEntity.internalServerError().body("文件上传失败：" + e.getMessage());
        }
    }
} 