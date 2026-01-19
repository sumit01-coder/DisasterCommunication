package com.example.disastercomm.utils;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.Projection;

public class CirclePulseOverlay extends Overlay {

    private GeoPoint location;
    private final Paint paint;
    private float radius = 0;
    private final float maxRadius;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable runner;
    private boolean isRunning = false;

    public CirclePulseOverlay(GeoPoint location, int color, float maxRadiusPixels) {
        this.location = location;
        this.maxRadius = maxRadiusPixels;

        paint = new Paint();
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);

        runner = new Runnable() {
            @Override
            public void run() {
                if (!isRunning)
                    return;

                radius += 2; // Speed of expansion
                int alpha = (int) (255 * (1 - (radius / maxRadius))); // Fade out
                if (alpha < 0)
                    alpha = 0;
                paint.setAlpha(alpha);

                if (radius > maxRadius) {
                    radius = 0;
                }

                // Redraw map
                // Note: We need a reference to map view to invalidate, but Overlay doesn't
                // store it by default.
                // It is passed in draw usually, but for animation we need external trigger or
                // hook.
                // Ideally this overlay should be managed by the MapView or have a callback.
                // For simplicity here, we assume the map invalidates frequently or we depend on
                // map interaction.
                // Better approach: Using MapView's postInvalidate from outside or passing
                // MapView to constructor.
            }
        };
    }

    // Updated constructor to accept MapView for invalidation
    private MapView mapView;

    public CirclePulseOverlay(MapView mapView, GeoPoint location, int color, float maxRadiusPixels) {
        this(location, color, maxRadiusPixels);
        this.mapView = mapView;
    }

    @Override
    public void draw(Canvas canvas, MapView mapView, boolean shadow) {
        if (shadow)
            return;
        if (location == null)
            return;

        Projection projection = mapView.getProjection();
        Point point = new Point();
        projection.toPixels(location, point);

        canvas.drawCircle(point.x, point.y, radius, paint);

        // Loop animation
        if (isRunning) {
            long delay = 30; // ~30fps
            mapView.postInvalidateDelayed(delay);
            radius += 2;
            if (radius > maxRadius) {
                radius = 0;
            }
            int alpha = (int) (100 * (1 - (radius / maxRadius))); // Low opacity fill
            paint.setAlpha(alpha);
        }
    }

    public void start() {
        isRunning = true;
        radius = 0;
        if (mapView != null)
            mapView.postInvalidate();
    }

    public void stop() {
        isRunning = false;
    }

    public void setLocation(GeoPoint location) {
        this.location = location;
    }
}
