package com.example.tomatomall.service.serviceImpl;

import com.example.tomatomall.Util.SecurityUtil;
import com.example.tomatomall.exception.TomatoMallException;
import com.example.tomatomall.po.Comment;
import com.example.tomatomall.repository.CommentRepository;
import com.example.tomatomall.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommentServiceImpl implements CommentService {

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private SecurityUtil securityUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Comment addComment(String productId, String commentStr) {
        if (securityUtil.getCurrentAccount() == null) {
            throw TomatoMallException.notLogin();
        }

        Comment comment = new Comment();
        comment.setProductId(Integer.valueOf(productId));
        comment.setCommentStr(commentStr);
        comment.setUsername(securityUtil.getCurrentAccount().getUsername());

        try {
            return commentRepository.save(comment);
        } catch (DataAccessException ex) {
            throw TomatoMallException.concurrentUpdate();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String deleteComment(String commentId) {
        Comment comment = commentRepository.findById(Integer.valueOf(commentId))
                .orElseThrow(TomatoMallException::commentNotExist);

        if (!comment.getUsername().equals(securityUtil.getCurrentAccount().getUsername())) {
            throw TomatoMallException.deleteCommentError();
        }

        try {
            commentRepository.delete(comment);
            return "删除成功";
        } catch (DataAccessException ex) {
            throw TomatoMallException.concurrentUpdate();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Comment> getProductComments(String productId) {
        return commentRepository.findByProductId(Integer.valueOf(productId));
    }
}
