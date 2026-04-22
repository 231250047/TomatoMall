package com.example.tomatomall.po;

import lombok.*;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Table(name = "comment")
@Getter
@Setter
@Data
@NoArgsConstructor(force = true)
@AllArgsConstructor
@Entity
public class Comment {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Integer id;

    @Basic
    @Column(name = "product_id")
    private Integer productId;

    @Basic
    @Column(name = "username")
    private String username;

    @Basic
    @Column(name = "comment_str")
    private String commentStr;

    @Basic
    @Column(name = "create_time")
    private LocalDateTime createTime;


    @PrePersist
    public void prePersist() {
        createTime = LocalDateTime.now();
    }

}
