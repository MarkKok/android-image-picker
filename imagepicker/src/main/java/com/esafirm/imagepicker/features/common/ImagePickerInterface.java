package com.esafirm.imagepicker.features.common;

import com.esafirm.imagepicker.model.Image;

import java.util.ArrayList;

public interface ImagePickerInterface {
    void onImagePicked(int pickCount);
    void returnPickedImages(ArrayList<Image> images);
}
