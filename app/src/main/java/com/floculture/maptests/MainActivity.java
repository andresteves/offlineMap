package com.floculture.maptests;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.widget.Toast;

import org.osmdroid.api.IMapController;
import org.osmdroid.bonuspack.routing.OSRMRoadManager;
import org.osmdroid.bonuspack.routing.Road;
import org.osmdroid.bonuspack.routing.RoadManager;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.cachemanager.CacheManager;
import org.osmdroid.tileprovider.modules.ArchiveFileFactory;
import org.osmdroid.tileprovider.modules.IArchiveFile;
import org.osmdroid.tileprovider.modules.OfflineTileProvider;
import org.osmdroid.tileprovider.modules.SqliteArchiveTileWriter;
import org.osmdroid.tileprovider.tilesource.FileBasedTileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ItemizedIconOverlay;
import org.osmdroid.views.overlay.ItemizedOverlayWithFocus;
import org.osmdroid.views.overlay.OverlayItem;

import java.io.File;
import java.util.ArrayList;
import java.util.Set;

public class MainActivity extends Activity {

    CacheManager mgr=null;
    MapView map;
    SqliteArchiveTileWriter writer=null;
    RoadManager roadManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context ctx = getApplicationContext();
        //important! set your user agent to prevent getting banned from the osm servers
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        setContentView(R.layout.activity_main);

        map = (MapView) findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setBuiltInZoomControls(true);
        map.setMultiTouchControls(true);

        IMapController mapController = map.getController();
        mapController.setZoom(17);
        GeoPoint startPoint = new GeoPoint(54.96713, -1.59479);
        mapController.setCenter(startPoint);

        roadManager = new OSRMRoadManager(this);


        updateEstimate(true);
        addPins();
    }

    public void onResume(){
        super.onResume();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().save(this, prefs);
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
    }

    /**
     *
     */
    private void addPins()
    {
        //your items
        ArrayList<OverlayItem> items = new ArrayList<OverlayItem>();
        items.add(new OverlayItem("NDC", "Description", new GeoPoint(54.96713, -1.59479))); // Lat/Lon decimal degrees

        //the overlay
        ItemizedOverlayWithFocus<OverlayItem> mOverlay = new ItemizedOverlayWithFocus<OverlayItem>(items,
                new ItemizedIconOverlay.OnItemGestureListener<OverlayItem>() {
                    @Override
                    public boolean onItemSingleTapUp(final int index, final OverlayItem item) {
                        //do something
                        return true;
                    }
                    @Override
                    public boolean onItemLongPress(final int index, final OverlayItem item) {
                        return false;
                    }
                },this);
        mOverlay.setFocusItemsOnTap(true);

        map.getOverlays().add(mOverlay);
    }

    /**
     * @param persistentStorage
     * Start to download portion of map that interest us, persistentStorage is used for persist the data,
     * otherwise it will use the cache system that expires after 2 weeks
     */
    private void updateEstimate(boolean persistentStorage) {

        try {
            if (persistentStorage) {
                String outputName = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "osmdroid" + File.separator + "persist";
                writer = new SqliteArchiveTileWriter(outputName);
                mgr = new CacheManager(map, writer);
            } else {
                if (mgr == null)
                    mgr = new CacheManager(map);
            }


            //nesw
            BoundingBox bb = new BoundingBox(54.96817, -1.59373, 54.96591, -1.59771);
            int tilecount = mgr.possibleTilesInArea(bb, 16, 18);

            Toast.makeText(MainActivity.this, "Tiles count: " + tilecount, Toast.LENGTH_SHORT).show();

            //this triggers the download
            mgr.downloadAreaAsync(this, bb, 16, 18, new CacheManager.CacheManagerCallback() {
                @Override
                public void onTaskComplete() {
                    Toast.makeText(MainActivity.this, "Download complete!", Toast.LENGTH_LONG).show();
                    addOverlays();
                }

                @Override
                public void onTaskFailed(int errors) {
                    Toast.makeText(MainActivity.this, "Download complete with " + errors + " errors", Toast.LENGTH_LONG).show();
                }

                @Override
                public void updateProgress(int progress, int currentZoomLevel, int zoomMin, int zoomMax) {
                    //NOOP since we are using the build in UI
                }

                @Override
                public void downloadStarted() {
                    //NOOP since we are using the build in UI
                }

                @Override
                public void setPossibleTilesInArea(int total) {
                    //NOOP since we are using the build in UI
                }
            });

        }catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }


    @Override
    public void onPause(){
        super.onPause();
    }

    /**
     *  Check for offline map provider. Replace TileLoadFailureImage with suitable image
     */
    public void addOverlays() {
        //not even needed since we are using the offline tile provider only
        map.setUseDataConnection(false);

        //https://github.com/osmdroid/osmdroid/issues/330
        //custom image placeholder for files that aren't available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            map.getTileProvider().setTileLoadFailureImage(this.getDrawable(R.mipmap.ic_map_fail));
        }else{
            map.getTileProvider().setTileLoadFailureImage(getResources().getDrawable(R.mipmap.ic_map_fail));
        }

        //first we'll look at the default location for tiles that we support
        File f = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/osmdroid/");
        if (f.exists()) {

            File[] list = f.listFiles();
            if (list != null) {
                for (int i = 0; i < list.length; i++) {
                    if (list[i].isDirectory()) {
                        continue;
                    }
                    String name = list[i].getName().toLowerCase();
                    if (!name.contains(".")) {
                        continue; //skip files without an extension
                    }
                    name = name.substring(name.lastIndexOf(".") + 1);
                    if (name.length() == 0) {
                        continue;
                    }
                    if (ArchiveFileFactory.isFileExtensionRegistered(name)) {
                        try {

                            //ok found a file we support and have a driver for the format, for this demo, we'll just use the first one

                            //create the offline tile provider, it will only do offline file archives
                            //again using the first file
                            OfflineTileProvider tileProvider = new OfflineTileProvider(new SimpleRegisterReceiver(MainActivity.this),
                                    new File[]{list[i]});

                            //tell osmdroid to use that provider instead of the default rig which is (asserts, cache, files/archives, online
                            map.setTileProvider(tileProvider);

                            //this bit enables us to find out what tiles sources are available. note, that this action may take some time to run
                            //and should be ran asynchronously. we've put it inline for simplicity

                            String source = "";
                            IArchiveFile[] archives = tileProvider.getArchives();
                            if (archives.length > 0) {
                                //cheating a bit here, get the first archive file and ask for the tile sources names it contains
                                Set<String> tileSources = archives[0].getTileSources();
                                //presumably, this would be a great place to tell your users which tiles sources are available
                                if (!tileSources.isEmpty()) {
                                    //ok good, we found at least one tile source, create a basic file based tile source using that name
                                    //and set it. If we don't set it, osmdroid will attempt to use the default source, which is "MAPNIK",
                                    //which probably won't match your offline tile source, unless it's MAPNIK
                                    source = tileSources.iterator().next();
                                    this.map.setTileSource(FileBasedTileSource.getSource(source));
                                } else {
                                    this.map.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE);
                                }

                            } else {
                                this.map.setTileSource(TileSourceFactory.DEFAULT_TILE_SOURCE);
                            }

                            Snackbar.make(map, "Using " + list[i].getAbsolutePath() + " " + source, Snackbar.LENGTH_SHORT).show();
                            this.map.invalidate();
                            return;
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            }
            Toast.makeText(this, f.getAbsolutePath() + " did not have any files I can open! Try using MOBAC", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, f.getAbsolutePath() + " dir not found!", Toast.LENGTH_SHORT).show();
        }

    }
}
