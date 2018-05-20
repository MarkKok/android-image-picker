package com.esafirm.imagepicker.features.common;

import com.esafirm.imagepicker.model.Image;

import java.util.List;

public interface ImagePickerInterface {
    void onImagePicked(int pickCount);
    void returnPickedImages(List<Image> images);
}
