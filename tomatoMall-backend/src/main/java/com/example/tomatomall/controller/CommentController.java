package com.example.tomatomall.controller;

import com.example.tomatomall.po.Comment;
import com.example.tomatomall.service.CommentService;
import com.example.tomatomall.vo.Response;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/comment")
public class CommentController {

    @Resource
    CommentService commentService;

    @PostMapping("/{productId}")
    public Response<Comment> addComment(@PathVariable String productId, @RequestBody Map<String, String> commentRequest) {
        String commentStr = commentRequest.get("commentStr");
        return Response.buildSuccess(commentService.addComment(productId, commentStr));
    }

    @DeleteMapping("/{commentId}")
    public Response<String> deleteComment(@PathVariable String commentId) {
        return Response.buildSuccess(commentService.deleteComment(commentId));
    }

    @GetMapping("/{productId}")
    public Response<List<Comment>> getProductComments(@PathVariable String productId) {
        return Response.buildSuccess(commentService.getProductComments(productId));
    }
}
