package com.example.tomatomall.po;

import com.example.tomatomall.vo.AdvertisementVO;
import lombok.*;

import javax.persistence.*;


@Table(name = "advertisements")
@Data
@NoArgsConstructor(force = true)
@AllArgsConstructor
@Entity
public class Advertisement {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Integer id;

    @Basic
    @Column(name="title",nullable = false, length = 50)
    private String title;

    @Basic
    @Column(name="content",nullable = false, length = 500)
    private String content;

    @Basic
    @Column(name = "image_url", nullable = false, length = 500)
    private String imgUrl;

    @Basic
    @Column(name = "related_url", nullable = true, length = 500)
    private String relatedUrl;
    // getter 和 setter 省略

    public AdvertisementVO toVO() {
        AdvertisementVO advertisementVO = new AdvertisementVO();
        advertisementVO.setId(this.id);
        advertisementVO.setTitle(this.title);
        advertisementVO.setContent(this.content);
        advertisementVO.setImgUrl(this.imgUrl);
        return advertisementVO;
    }
}
