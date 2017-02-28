package com.cdk.bettermapsearch;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.OvershootInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.RelativeLayout;

import com.cdk.bettermapsearch.clustering.CachedClusterManager;
import com.cdk.bettermapsearch.clustering.CustomMarkerRenderer;
import com.cdk.bettermapsearch.interfaces.MapClusterItem;
import com.cdk.bettermapsearch.interfaces.MapReadyCallback;
import com.cdk.bettermapsearch.interfaces.SelectedItemCallback;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterItem;
import com.google.maps.android.clustering.ClusterManager;
import com.lsjwzh.widget.recyclerviewpager.RecyclerViewPager;

import java.util.ArrayList;
import java.util.List;

import fr.castorflex.android.smoothprogressbar.SmoothProgressBar;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;

public class MapPagerView<T extends MapClusterItem> extends RelativeLayout implements
        OnMapReadyCallback,
        GoogleMap.InfoWindowAdapter,
        GoogleMap.OnMapClickListener,
        GoogleMap.OnInfoWindowClickListener,
        RecyclerViewPager.OnPageChangedListener,
        ClusterManager.OnClusterClickListener<T>,
        ClusterManager.OnClusterInfoWindowClickListener<T>,
        ClusterManager.OnClusterItemClickListener<T>,
        ClusterManager.OnClusterItemInfoWindowClickListener<T>,
        SelectedItemCallback<T> {

    public MapPagerView(Context context) {
        super(context);
        initialize();
    }

    public MapPagerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public MapPagerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    private MapView mapView;
    private RecyclerViewPager viewPager;
    private SmoothProgressBar progressBar;

    private CachedClusterManager<T> clusterManager;
    private GoogleMap googleMap;
    private T currentlySelectedItem;
    private MapPagerAdapter pagerAdapter;
    private int phoneHeight;
    private CustomMarkerRenderer<T> markerRenderer;
    private MapReadyCallback<T> mapReadyCallback;
    private Subscription viewSubscriber;
    private boolean loading = false;

    private void initialize() {
        LayoutInflater.from(getContext()).inflate(R.layout.map_pager, this, true);
        mapView = (MapView) findViewById(R.id.map);
        viewPager = (RecyclerViewPager) findViewById(R.id.view_pager);
        progressBar = (SmoothProgressBar) findViewById(R.id.progress);

        viewPager.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        viewPager.addOnPageChangedListener(this);
        viewPager.setHasFixedSize(true);
        progressBar.progressiveStop();
    }

    //region Map and clustering callbacks

    @Override
    public View getInfoWindow(Marker marker) {
        return null;
    }

    @Override
    public View getInfoContents(Marker marker) {
        return null;
    }

    @Override
    public void onInfoWindowClick(Marker marker) {
        // no-op
    }

    @Override
    public void onMapClick(LatLng latLng) {
        dismissViewPager();
        markerRenderer.unselectAllItems();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.googleMap = googleMap;
        // Do some map stuff
        clusterManager = new CachedClusterManager<>(getContext(), googleMap);
        markerRenderer = mapReadyCallback.onMapReady(googleMap, clusterManager);
        markerRenderer.setItemCallback(this);
        clusterManager.setRenderer(markerRenderer);

        clusterManager.setOnClusterClickListener(this);
        clusterManager.setOnClusterInfoWindowClickListener(this);
        clusterManager.setOnClusterItemClickListener(this);
        clusterManager.setOnClusterItemInfoWindowClickListener(this);

        googleMap.setOnMarkerClickListener(clusterManager);
        googleMap.setOnCameraIdleListener(clusterManager);
        googleMap.setOnMapClickListener(this);

        googleMap.getUiSettings().setTiltGesturesEnabled(false);
        googleMap.getUiSettings().setIndoorLevelPickerEnabled(false);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        googleMap.getUiSettings().setMapToolbarEnabled(false);
    }

    @Override
    public boolean onClusterClick(Cluster<T> cluster) {
        if (currentlySelectedItem != null && markerRenderer.clusterContainsItem(cluster, currentlySelectedItem)) {
            markerRenderer.renderPreviousClusterAsUnselected();
        } else {
            currentlySelectedItem = null;
            dismissViewPager();
            markerRenderer.unselectAllItems();
        }

        // Zoom in the cluster. Need to create LatLngBounds and including all the cluster items
        // inside of bounds, then animate to center of the bounds.

        // Create the builder to collect all essential cluster items for the bounds.
        LatLngBounds.Builder builder = LatLngBounds.builder();
        for (ClusterItem item : cluster.getItems()) {
            builder.include(item.getPosition());
        }
        // Get the LatLngBounds
        final LatLngBounds bounds = builder.build();

        // Animate camera to the bounds
        try {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 300));
        } catch (IllegalStateException e) {
            // Screen size is too small, get rid of padding
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0));
        }

        return true;
    }

    @Override
    public void onClusterInfoWindowClick(Cluster<T> cluster) {
        // no-op
    }

    @Override
    public boolean onClusterItemClick(T clusterItem) {
        markerRenderer.renderClusterItemAsSelected(clusterItem);

        currentlySelectedItem = clusterItem;

        if (viewPager.getVisibility() != View.VISIBLE) {
            // This is to give the fragments some time to build their views
            showVehicleInfo();
        } else {
            viewPager.scrollToPosition(currentlySelectedItem.getIndex());
        }
        return false;
    }

    @Override
    public void onClusterItemInfoWindowClick(T t) {
        // no-op
    }

    //endregion

    @Override
    public void OnPageChanged(int size, int pos) {
        T clusterItem = clusterManager.getClusterItem(pos);

        if (!markerRenderer.renderClusterItemAsSelected(clusterItem)) {
            LatLng clusterPosition = markerRenderer.getClusterMarker(clusterManager.getClusterMarkerCollection().getMarkers(), clusterItem);
            googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.fromLatLngZoom(clusterPosition, googleMap.getCameraPosition().zoom)), 400, null);
        } else {
            googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.fromLatLngZoom(pagerAdapter.getItemPositionOnMap(pos), googleMap.getCameraPosition().zoom)), 400, null);
        }

        currentlySelectedItem = clusterItem;
    }

    //region wrappers for MapView lifecycle

    public void onCreate(Bundle savedInstanceState, int phoneHeight) {
        mapView.onCreate(savedInstanceState);

        this.phoneHeight = phoneHeight;
        viewPager.getLayoutParams().height = (int) (phoneHeight * 0.25);
    }

    public void getMapAsync(MapReadyCallback<T> callback) {
        this.mapReadyCallback = callback;
        mapView.getMapAsync(this);
    }

    public void onResume() {
        mapView.onResume();
    }

    public void onStart() {
        mapView.onStart();
    }

    public void onPause() {
        mapView.onPause();
    }

    public void onStop() {
        mapView.onStop();
    }

    public void onDestroy() {
        mapView.onDestroy();
    }

    public void onLowMemory() {
        mapView.onLowMemory();
    }

    //endregion

    //region override callbacks

    public void setOnInfoWindowClickListener(GoogleMap.OnInfoWindowClickListener listener) {
        if (googleMap != null) {
            googleMap.setOnInfoWindowClickListener(listener);
        }
    }

    public void setInfoWindowAdapter(GoogleMap.InfoWindowAdapter windowAdapter) {
        if (googleMap != null) {
            googleMap.setInfoWindowAdapter(windowAdapter);
        }
    }

    public void setOnMapClickListener(GoogleMap.OnMapClickListener mapClickListener) {
        if (googleMap != null) {
            googleMap.setOnMapClickListener(mapClickListener);
        }
    }

    @Nullable
    public UiSettings getUiSettings() {
        if (googleMap != null) {
            return googleMap.getUiSettings();
        }
        return null;
    }
    //endregion

    private void showVehicleInfo() {
        pagerAdapter.clearCallbacks();

        int pos = currentlySelectedItem != null ? currentlySelectedItem.getIndex() : viewPager.getCurrentPosition();

        List<Observable<Void>> observables = new ArrayList<>();

        for (int i = Math.max(pos - 1, 0); i <= Math.min(pagerAdapter.getItemCount() - 1, pos + 1); i++) {
            if (viewPager.findViewHolderForAdapterPosition(i) == null) {
                observables.add(Observable.create(new ViewCreatedObserver(pagerAdapter, i, viewPager)));
            }
        }

        if (observables.size() > 0) {
            if (viewSubscriber != null) {
                viewSubscriber.unsubscribe();
            }
            viewSubscriber = Observable.combineLatest(observables, args -> null)
                    .subscribeOn(AndroidSchedulers.mainThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Subscriber<Object>() {
                        @Override
                        public void onCompleted() {
                        }

                        @Override
                        public void onError(Throwable e) {
                        }

                        @Override
                        public void onNext(Object ignored) {
                            pagerAdapter.clearCallbacks();
                            showViewPager();
                        }
                    });
        } else {
            showViewPager();
        }

        viewPager.scrollToPosition(currentlySelectedItem.getIndex());
    }

    private void dismissViewPager() {
        if (viewPager.getVisibility() == View.VISIBLE) {
            int pos = viewPager.getCurrentPosition();

            int k = 0;
            final int max = Math.min(pagerAdapter.getItemCount() - 1, pos + 1);

            for (int i = Math.max(pos - 1, 0); i <= max; i++) {
                RecyclerView.ViewHolder holder = viewPager.findViewHolderForAdapterPosition(i);
                if (holder != null) {
                    View view = holder.itemView;

                    TranslateAnimation translateAnimation = new TranslateAnimation(0, 0, 0, phoneHeight);
                    translateAnimation.setDuration(400);
                    translateAnimation.setInterpolator(new AccelerateInterpolator());
                    translateAnimation.setStartOffset(k * 50);

                    int finalI = i;
                    translateAnimation.setAnimationListener(new Animation.AnimationListener() {
                        @Override
                        public void onAnimationStart(Animation animation) {
                        }

                        @Override
                        public void onAnimationEnd(Animation animation) {
                            view.setVisibility(View.GONE);
                            // if it's the final animation
                            if (finalI == max) {
                                viewPager.setVisibility(View.GONE);
                            }
                        }

                        @Override
                        public void onAnimationRepeat(Animation animation) {

                        }
                    });
                    view.startAnimation(translateAnimation);
                    k++;
                }
            }
        }
        currentlySelectedItem = null;
    }

    private void showViewPager() {
        viewPager.setVisibility(View.VISIBLE);

        int pos = currentlySelectedItem != null ? currentlySelectedItem.getIndex() : viewPager.getCurrentPosition();
        int k = 0;

        for (int i = Math.max(pos - 1, 0); i <= Math.min(pagerAdapter.getItemCount() - 1, pos + 1); i++) {
            RecyclerView.ViewHolder holder = viewPager.findViewHolderForAdapterPosition(i);
            final View itemView = holder.itemView;

            TranslateAnimation translateAnimation = new TranslateAnimation(0, 0, phoneHeight, 0);
            translateAnimation.setDuration(400);
            translateAnimation.setStartOffset(k * 50);
            translateAnimation.setInterpolator(new OvershootInterpolator(0.3f));
            translateAnimation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    itemView.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    itemView.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });

            itemView.startAnimation(translateAnimation);
            k++;
        }
    }

    // This is called every time data is refreshed
    public void setAdapter(MapPagerAdapter adapter) {
        if (pagerAdapter == null) {
            viewPager.setAdapter(adapter);
        } else {
            viewPager.swapAdapter(adapter, true);
        }
        this.pagerAdapter = adapter;
    }

    @SuppressWarnings("unchecked")
    public void updateMapItems(List<T> clusterItems) {
        for (int i = 0; i < clusterItems.size(); i++) {
            // set up each cluster item with the information it needs
            clusterItems.get(i).setIndex(i);
            clusterItems.get(i).setupPositionFromLatAndLon();
        }

        clusterManager.addItems(clusterItems);
        clusterManager.cluster();

        pagerAdapter.updateItems(clusterItems);
        setLoadingIndicator(false);
    }

    public void setLoadingIndicator(boolean loading) {
        if (loading == this.loading) {
            return;
        }
        if (loading) {
            progressBar.progressiveStart();
        } else {
            progressBar.progressiveStop();
        }
        this.loading = loading;
    }

    @Override
    public T getSelectedItem() {
        return currentlySelectedItem;
    }
}
