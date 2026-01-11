package com.example.tomatomall.vo;


import com.example.tomatomall.po.Advertisement;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
public class AdvertisementVO {
    private Integer id;
    private String title;
    private String content;
    private String imgUrl;
    private String relatedUrl;

    public Advertisement toPO() {
        Advertisement advertisement = new Advertisement();
        if(this.id!=null)advertisement.setId(this.id);
        advertisement.setTitle(this.title);
        advertisement.setContent(this.content);
        advertisement.setImgUrl(this.imgUrl);
        advertisement.setRelatedUrl(this.relatedUrl);
        return advertisement;
    }
}
