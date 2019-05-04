package com.esafirm.sample;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.esafirm.imagepicker.features.ImagePickerConfigFactory;
import com.esafirm.imagepicker.features.ImagePickerFragment;
import com.esafirm.imagepicker.features.common.ImagePickerInterface;
import com.esafirm.imagepicker.model.Image;

import java.util.ArrayList;

public class FragmentPickerDemoActivity extends AppCompatActivity implements ImagePickerInterface{

    private Button button;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fragment_picker_demo);

        button = findViewById(R.id.button);
        button.setOnClickListener(view -> collectPickedImages());

        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, ImagePickerFragment.newInstance(ImagePickerConfigFactory.createDefault()))
                .commit();
    }

    @Override
    public void onImagePicked(int pickCount) {
        button.setVisibility(pickCount > 0 ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    public void returnPickedImages(ArrayList<Image> images) {
        Toast.makeText(this, "received " + images.size() + " image(s)", Toast.LENGTH_SHORT).show();
    }

    private void collectPickedImages(){
        ImagePickerFragment imagePickerFragment = (ImagePickerFragment)
                getSupportFragmentManager().findFragmentById(R.id.fragment_container);

        if(imagePickerFragment != null)
            imagePickerFragment.onDone();
    }
}
