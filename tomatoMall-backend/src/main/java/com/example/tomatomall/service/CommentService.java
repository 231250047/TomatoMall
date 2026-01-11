package com.example.tomatomall.service;

import com.example.tomatomall.po.Comment;
import java.util.List;

public interface CommentService {
    Comment addComment(String productId, String commentStr);

    String deleteComment(String commentId);

    List<Comment> getProductComments(String productId);
}
