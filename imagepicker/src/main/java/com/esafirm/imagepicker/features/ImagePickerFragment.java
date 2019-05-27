package com.esafirm.imagepicker.features;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.esafirm.imagepicker.R;
import com.esafirm.imagepicker.features.camera.CameraHelper;
import com.esafirm.imagepicker.features.cameraonly.CameraOnlyConfig;
import com.esafirm.imagepicker.features.common.BaseConfig;
import com.esafirm.imagepicker.features.common.ImagePickerInterface;
import com.esafirm.imagepicker.features.recyclers.EndlessRecyclerViewScrollListener;
import com.esafirm.imagepicker.features.recyclers.RecyclerViewManager;
import com.esafirm.imagepicker.helper.ConfigUtils;
import com.esafirm.imagepicker.helper.ImagePickerPreferences;
import com.esafirm.imagepicker.helper.IpLogger;
import com.esafirm.imagepicker.model.Folder;
import com.esafirm.imagepicker.model.Image;
import com.esafirm.imagepicker.view.SnackBarView;

import java.util.ArrayList;
import java.util.List;

import static com.esafirm.imagepicker.helper.ImagePickerPreferences.PREF_WRITE_EXTERNAL_STORAGE_REQUESTED;

public class ImagePickerFragment extends Fragment implements ImagePickerView{

    private static final String STATE_KEY_CAMERA_MODULE = "Key.CameraModule";

    private static final int RC_CAPTURE = 2000;

    private static final int RC_PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 23;
    private static final int RC_PERMISSION_REQUEST_CAMERA = 24;

    private IpLogger logger = IpLogger.getInstance();

    private ProgressBar progressBar;
    private TextView emptyTextView;
    private RecyclerView recyclerView;
    private SnackBarView snackBarView;

    private RecyclerViewManager recyclerViewManager;

    private ImagePickerPresenter presenter;
    private ImagePickerPreferences preferences;
    private ImagePickerConfig config;

    private Handler handler;
    private ContentObserver observer;

    private boolean isCameraOnly;
    private int page = 0;
    private static final int PAGE_SIZE = 50;

    private ImagePickerInterface mCallback;

    public static ImagePickerFragment newInstance(ImagePickerConfig imagePickerConfig){
        ImagePickerFragment fragment = new ImagePickerFragment();
        Bundle args = new Bundle();
        args.putParcelable(ImagePickerConfig.class.getSimpleName(), imagePickerConfig);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.ef_fragment_image_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        isCameraOnly = getArguments().containsKey(CameraOnlyConfig.class.getSimpleName());

        setupComponents();

        if (isCameraOnly) {
            if (savedInstanceState == null) {
                captureImageWithPermission();
            }
        } else {
            ImagePickerConfig config = getImagePickerConfig();
            if (config != null) {
                setupView(view);
                setupRecyclerView(config);
            }
        }
    }

    private BaseConfig getBaseConfig() {
        return isCameraOnly
                ? getCameraOnlyConfig()
                : getImagePickerConfig();
    }

    private CameraOnlyConfig getCameraOnlyConfig() {
        return getArguments().getParcelable(CameraOnlyConfig.class.getSimpleName());
    }

    @Nullable
    private ImagePickerConfig getImagePickerConfig() {
        if (config == null) {
            config = getArguments().getParcelable(ImagePickerConfig.class.getSimpleName());
        }
        return config;
    }

    private void setupView(View view){
        progressBar = view.findViewById(R.id.progress_bar);
        emptyTextView = view.findViewById(R.id.tv_empty_images);
        recyclerView = view.findViewById(R.id.recyclerView);
        snackBarView = view.findViewById(R.id.ef_snackbar);
    }

    private void setupRecyclerView(ImagePickerConfig config) {
        recyclerViewManager = new RecyclerViewManager(
                recyclerView,
                config,
                getResources().getConfiguration().orientation
        );

        recyclerViewManager.setupAdapters((isSelected) -> recyclerViewManager.selectImage(isSelected)
                , bucket -> setImageAdapter(bucket.getImages()));

        recyclerViewManager.setImageSelectedListener(selectedImage -> {
            mCallback.onImagePicked(selectedImage.size());
            if (ConfigUtils.shouldReturn(config, false) && !selectedImage.isEmpty()) {
                onDone();
            }
        });

        recyclerView.addOnScrollListener(new EndlessRecyclerViewScrollListener(recyclerViewManager.getLayoutManager()) {
            @Override
            public void onLoadMore() {
                page++;
                getData();
            }
        });
    }

    private void setupComponents() {
        preferences = new ImagePickerPreferences(getActivity());
        presenter = new ImagePickerPresenter(new ImageFileLoader(getActivity()));
        presenter.attachView(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isCameraOnly) {
            getDataWithPermission();
        }
    }

    /*@Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_KEY_CAMERA_MODULE, presenter.getCameraModule());
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        presenter.setCameraModule((DefaultCameraModule) savedInstanceState.getSerializable(STATE_KEY_CAMERA_MODULE));
    }*/

    /**
     * Set image adapter
     * 1. Set new data
     * 2. Update item decoration
     * 3. Update title
     */
    private void setImageAdapter(List<Image> images) {
        recyclerViewManager.setImageAdapter(images);
        invalidateTitle();
    }

    private void setFolderAdapter(List<Folder> folders) {
        recyclerViewManager.setFolderAdapter(folders);
        invalidateTitle();
    }

    private void invalidateTitle() {
        //supportInvalidateOptionsMenu();
        //actionBar.setTitle(recyclerViewManager.getTitle());
    }

    /*@Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem menuCamera = menu.findItem(R.id.menu_camera);
        if (menuCamera != null) {
            ImagePickerConfig imagePickerConfig = getImagePickerConfig();
            if (imagePickerConfig != null) {
                menuCamera.setVisible(imagePickerConfig.isShowCamera());
            }
        }

        MenuItem menuDone = menu.findItem(R.id.menu_done);
        if (menuDone != null) {
            menuDone.setVisible(recyclerViewManager.isShowDoneButton());
        }
        return super.onPrepareOptionsMenu(menu);
    }*/

    /**
     * Handle option menu's click event
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            //onBackPressed();
            return true;
        }
        if (id == R.id.menu_done) {
            onDone();
            return true;
        }
        if (id == R.id.menu_camera) {
            captureImageWithPermission();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * On finish selected image
     * Get all selected images then return image to caller activity
     */
    public void onDone() {
        presenter.onDoneSelectImages(recyclerViewManager.getSelectedImages());
    }

    /**
     * Config recyclerView when configuration changed
     */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (recyclerViewManager != null) {
            // recyclerViewManager can be null here if we use cameraOnly mode
            recyclerViewManager.changeOrientation(newConfig.orientation);
        }
    }

    /**
     * Check permission
     */
    private void getDataWithPermission() {
        int rc = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            getData();
        } else {
            requestWriteExternalPermission();
        }
    }

    private void getData() {
        ImagePickerConfig config = getImagePickerConfig();
        presenter.abortLoad();
        if (config != null) {
            presenter.loadImages(config, page * PAGE_SIZE, PAGE_SIZE);
        }
    }

    /**
     * Request for permission
     * If permission denied or app is first launched, request for permission
     * If permission denied and user choose 'Never Ask Again', show snackbar with an action that navigate to app settings
     */
    private void requestWriteExternalPermission() {
        logger.w("Write External permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};

        if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            ActivityCompat.requestPermissions(getActivity(), permissions, RC_PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE);
        } else {
            final String permission = PREF_WRITE_EXTERNAL_STORAGE_REQUESTED;
            if (!preferences.isPermissionRequested(permission)) {
                preferences.setPermissionRequested(permission);
                ActivityCompat.requestPermissions(getActivity(), permissions, RC_PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE);
            } else {
                snackBarView.show(R.string.ef_msg_no_write_external_permission, v -> openAppSettings());
            }
        }
    }

    private void requestCameraPermissions() {
        logger.w("Write External permission is not granted. Requesting permission");

        ArrayList<String> permissions = new ArrayList<>(2);

        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA);
        }
        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        if (checkForRationale(permissions)) {
            ActivityCompat.requestPermissions(getActivity(), permissions.toArray(new String[permissions.size()]), RC_PERMISSION_REQUEST_CAMERA);
        } else {
            final String permission = ImagePickerPreferences.PREF_CAMERA_REQUESTED;
            if (!preferences.isPermissionRequested(permission)) {
                preferences.setPermissionRequested(permission);
                ActivityCompat.requestPermissions(getActivity(), permissions.toArray(new String[permissions.size()]), RC_PERMISSION_REQUEST_CAMERA);
            } else {
                if (isCameraOnly) {
                    Toast.makeText(getActivity(),
                            getString(R.string.ef_msg_no_camera_permission), Toast.LENGTH_SHORT).show();
                    getActivity().finish();
                } else {
                    snackBarView.show(R.string.ef_msg_no_camera_permission, v -> openAppSettings());
                }
            }
        }
    }

    private boolean checkForRationale(List<String> permissions) {
        for (int i = 0, size = permissions.size(); i < size; i++) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), permissions.get(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Handle permission results
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case RC_PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE: {
                if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    logger.d("Write External permission granted");
                    getData();
                    return;
                }
                logger.e("Permission not granted: results len = " + grantResults.length +
                        " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));
                getActivity().finish();
            }
            break;
            case RC_PERMISSION_REQUEST_CAMERA: {
                if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    logger.d("Camera permission granted");
                    captureImage();
                    return;
                }
                logger.e("Permission not granted: results len = " + grantResults.length +
                        " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));
                getActivity().finish();
                break;
            }
            default: {
                logger.d("Got unexpected permission result: " + requestCode);
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                break;
            }
        }
    }

    /**
     * Open app settings screen
     */
    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getActivity().getPackageName(), null));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    /**
     * Check if the captured image is stored successfully
     * Then reload data
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_CAPTURE) {
            if (resultCode == Activity.RESULT_OK) {
                presenter.finishCaptureImage(getActivity(), data, getBaseConfig());
            } else if (resultCode == Activity.RESULT_CANCELED && isCameraOnly) {
                presenter.abortCaptureImage();
                getActivity().finish();
            }
        }
    }

    /**
     * Request for camera permission
     */
    private void captureImageWithPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final boolean isCameraGranted = ActivityCompat
                    .checkSelfPermission(getActivity(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
            final boolean isWriteGranted = ActivityCompat
                    .checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
            if (isCameraGranted && isWriteGranted) {
                captureImage();
            } else {
                logger.w("Camera permission is not granted. Requesting permission");
                requestCameraPermissions();
            }
        } else {
            captureImage();
        }
    }

    /**
     * Start camera intent
     * Create a temporary file and pass file Uri to camera intent
     */
    private void captureImage() {
        if (!CameraHelper.checkCameraAvailability(getActivity())) {
            return;
        }
        presenter.captureImage(getActivity(), getBaseConfig(), RC_CAPTURE);
    }


    @Override
    public void onStart() {
        super.onStart();

        if (isCameraOnly) {
            return;
        }

        if (handler == null) {
            handler = new Handler();
        }
        observer = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                getData();
            }
        };
        getActivity().getContentResolver().registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, observer);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (presenter != null) {
            presenter.abortLoad();
            presenter.detachView();
        }

        if (observer != null) {
            getActivity().getContentResolver().unregisterContentObserver(observer);
            observer = null;
        }

        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            handler = null;
        }
    }

    /* --------------------------------------------------- */
    /* > View Methods */
    /* --------------------------------------------------- */

    @Override
    public void finishPickImages(List<Image> images) {
        ArrayList<Image> imageArrayList = new ArrayList<>(images);
        mCallback.returnPickedImages(imageArrayList);
        /*Intent data = new Intent();
        data.putParcelableArrayListExtra(IpCons.EXTRA_SELECTED_IMAGES, (ArrayList<? extends Parcelable>) images);
        setResult(RESULT_OK, data);
        finish();*/
    }

    @Override
    public void showCapturedImage() {
        getDataWithPermission();
    }

    @Override
    public void showFetchCompleted(List<Image> images, List<Folder> folders) {
        ImagePickerConfig config = getImagePickerConfig();
        if (config != null && config.isFolderMode()) {
            setFolderAdapter(folders);
        } else {
            setImageAdapter(images);
        }
    }

    @Override
    public void showError(Throwable throwable) {
        String message = "Unknown Error";
        if (throwable != null && throwable instanceof NullPointerException) {
            message = "Images not exist";
        }
        Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(isLoading ? View.GONE : View.VISIBLE);
        emptyTextView.setVisibility(View.GONE);
    }

    @Override
    public void showEmpty() {
        progressBar.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        emptyTextView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        try {
            mCallback = (ImagePickerInterface) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString()
                    + " must implement OnHeadlineSelectedListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mCallback = null;
    }
}
