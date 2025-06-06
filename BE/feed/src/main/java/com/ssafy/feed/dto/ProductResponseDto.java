package com.ssafy.feed.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponseDto {
    private int productId;
    private int categoryId;
    private String productName;
    private Boolean isFavorite;
    private int price;
    private String productUrl;
    private String productImage;
    private String manufacturer;
    private List<ProductDescriptionDTO> descriptions;
}