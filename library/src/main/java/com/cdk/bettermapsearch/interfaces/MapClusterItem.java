package com.cdk.bettermapsearch.interfaces;

import com.google.maps.android.clustering.ClusterItem;

public interface MapClusterItem extends ClusterItem {

    int getIndex();

    void setIndex(int index);

    void buildPositionFromLatAndLng();

    boolean isViewed();

    void setIsViewed();
}
