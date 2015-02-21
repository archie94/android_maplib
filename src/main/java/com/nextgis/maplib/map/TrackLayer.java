/*
 * Project:  NextGIS Mobile
 * Purpose:  Mobile GIS for Android.
 * Authors:  Stanislav Petriakov
 * *****************************************************************************
 * Copyright (c) 2015 NextGIS, info@nextgis.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextgis.maplib.map;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.text.TextUtils;
import com.nextgis.maplib.api.IGISApplication;
import com.nextgis.maplib.display.TrackRenderer;
import com.nextgis.maplib.util.Constants;

import java.io.File;


public class TrackLayer
        extends Layer
{
    public static final String TABLE_TRACKS      = "tracks";
    public static final String TABLE_TRACKPOINTS = "trackpoints";

    public static final String FIELD_ID      = "_id";
    public static final String FIELD_NAME    = "name";
    public static final String FIELD_START   = "start";
    public static final String FIELD_END     = "end";
    public static final String FIELD_VISIBLE = "visible";

    public static final String FIELD_LON       = "lon";
    public static final String FIELD_LAT       = "lat";
    public static final String FIELD_ELE       = "ele";
    public static final String FIELD_FIX       = "fix";
    public static final String FIELD_SAT       = "sat";
    public static final String FIELD_TIMESTAMP = "time";
    public static final String FIELD_SESSION   = "session";

    static final String DB_CREATE_TRACKS      =
            "CREATE TABLE IF NOT EXISTS " + TABLE_TRACKS + " (" +
            FIELD_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            FIELD_NAME + " TEXT NOT NULL, " +
            FIELD_START + " INTEGER NOT NULL, " +
            FIELD_END + " INTEGER, " +
            FIELD_VISIBLE + " INTEGER NOT NULL);";
    static final String DB_CREATE_TRACKPOINTS =
            "CREATE TABLE IF NOT EXISTS " + TABLE_TRACKPOINTS + " (" +
//            FIELD_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            FIELD_LON + " REAL NOT NULL, " +
            FIELD_LAT + " REAL NOT NULL, " +
            FIELD_ELE + " REAL, " +
            FIELD_FIX + " TEXT, " +
            FIELD_SAT + " INTEGER, " +
            FIELD_TIMESTAMP + " INTEGER NOT NULL, " +
            FIELD_SESSION + " INTEGER NOT NULL, FOREIGN KEY(" + FIELD_SESSION + ") REFERENCES " +
            TABLE_TRACKS + "(" + FIELD_ID + "));";

    private static final int TYPE_TRACKS       = 1;
    private static final int TYPE_TRACKPOINTS  = 2;
    private static final int TYPE_SINGLE_TRACK = 3;

    private static String CONTENT_TYPE, CONTENT_TYPE_TRACKPOINTS, CONTENT_ITEM_TYPE;

    protected int    mColor;
    protected Cursor mCursor;
    String         mAuthority;
    SQLiteDatabase mSQLiteDatabase;
    private UriMatcher mUriMatcher;
    private Uri        mContentUriTracks, mContentUriTrackpoints;
    private MapContentProviderHelper mMap;


    public TrackLayer(
            Context context,
            File path)
    {
        super(context, path);

        if (!(getContext() instanceof IGISApplication)) {
            throw new IllegalArgumentException(
                    "The context should be the instance of IGISApplication");
        }

        IGISApplication app = (IGISApplication) getContext();
        mMap = (MapContentProviderHelper) app.getMap();
        mAuthority = app.getAuthority();

        if (mMap == null) {
            throw new IllegalArgumentException(
                    "Cannot get access to DB (context's MapBase is null)");
        }

        mContentUriTracks = Uri.parse("content://" + mAuthority + "/" + TABLE_TRACKS);
        mContentUriTrackpoints = Uri.parse("content://" + mAuthority + "/" + TABLE_TRACKPOINTS);

        mUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        mUriMatcher.addURI(mAuthority, TABLE_TRACKS, TYPE_TRACKS);
        mUriMatcher.addURI(mAuthority, TABLE_TRACKS + "/#", TYPE_SINGLE_TRACK);
        mUriMatcher.addURI(mAuthority, TABLE_TRACKPOINTS, TYPE_TRACKPOINTS);

        CONTENT_TYPE = "vnd.android.cursor.dir/vnd." + mAuthority + "." + TABLE_TRACKS;
        CONTENT_TYPE_TRACKPOINTS =
                "vnd.android.cursor.dir/vnd." + mAuthority + "." + TABLE_TRACKPOINTS;
        CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd." + mAuthority + "." + TABLE_TRACKS;

        initDB();

        mLayerType = Constants.LAYERTYPE_TRACKS;
        mRenderer = new TrackRenderer(this);
    }


    public void loadTracks()
    {
        String[] proj = new String[] {FIELD_ID};
        String selection = FIELD_VISIBLE + " = 1";

        mCursor =
                mContext.getContentResolver().query(mContentUriTracks, proj, selection, null, null);
    }


    public Cursor getTrack(int position)
    {
        if (mCursor == null) {
            throw new RuntimeException("Tracks' cursor is null");
        }

        mCursor.moveToPosition(position);
        String[] proj = new String[] {FIELD_LON, FIELD_LAT};
        String id = mCursor.getString(mCursor.getColumnIndex(FIELD_ID));

        return mContext.getContentResolver()
                       .query(Uri.withAppendedPath(mContentUriTracks, id), proj, null, null, null);
    }


    public int getColor()
    {
        return mColor;
    }


    public int getTracksCount()
    {
        if (mCursor == null) {
            return 0;
        }

        return mCursor.getCount();
    }


    private void initDB()
    {
        mSQLiteDatabase = mMap.getDatabase(false);

//        mSQLiteDatabase.execSQL("DROP TABLE IF EXISTS TRACKPOINTS;");
//        mSQLiteDatabase.execSQL("DROP TABLE IF EXISTS TRACKS;");

        mSQLiteDatabase.execSQL(DB_CREATE_TRACKS);
        mSQLiteDatabase.execSQL(DB_CREATE_TRACKPOINTS);
    }


    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder)
    {
        mSQLiteDatabase = mMap.getDatabase(true);
        Cursor cursor;

        switch (mUriMatcher.match(uri)) {
            case TYPE_TRACKS:
                cursor = mSQLiteDatabase.query(TABLE_TRACKS, projection, selection, selectionArgs,
                                               null, null, sortOrder);
                cursor.setNotificationUri(getContext().getContentResolver(), mContentUriTracks);
                return cursor;
            case TYPE_TRACKPOINTS:
                cursor = mSQLiteDatabase.query(TABLE_TRACKPOINTS, projection, selection,
                                               selectionArgs, null, null, sortOrder);

                cursor.setNotificationUri(getContext().getContentResolver(),
                                          mContentUriTrackpoints);
                return cursor;
            case TYPE_SINGLE_TRACK:
                String id = uri.getLastPathSegment();

                if (TextUtils.isEmpty(selection)) {
                    selection = FIELD_SESSION + " = " + id;
                } else {
                    selection = selection + " AND " + FIELD_SESSION + " = " + id;
                }

                cursor = mSQLiteDatabase.query(TABLE_TRACKPOINTS, projection, selection,
                                               selectionArgs, null, null, sortOrder);
                return cursor;
            default:
                throw new IllegalArgumentException("Wrong tracks URI: " + uri);
        }
    }


    public Uri insert(
            Uri uri,
            ContentValues values)
    {
        mSQLiteDatabase = mMap.getDatabase(false);
        long id;
        Uri inserted;

        switch (mUriMatcher.match(uri)) {
            case TYPE_SINGLE_TRACK:
                values.remove(FIELD_ID);
            case TYPE_TRACKS:
                id = mSQLiteDatabase.insert(TABLE_TRACKS, null, values);
                inserted = ContentUris.withAppendedId(mContentUriTracks, id);
                break;
            case TYPE_TRACKPOINTS:
                id = mSQLiteDatabase.insert(TABLE_TRACKPOINTS, null, values);
                inserted = ContentUris.withAppendedId(mContentUriTrackpoints, id);
                break;
            default:
                throw new IllegalArgumentException("Wrong tracks URI: " + uri);
        }

        getContext().getContentResolver().notifyChange(inserted, null);

        return inserted;
    }


    public int delete(
            Uri uri,
            String selection,
            String[] selectionArgs)
    {
        mSQLiteDatabase = mMap.getDatabase(false);

        switch (mUriMatcher.match(uri)) {
            case TYPE_TRACKS:
                String trackpointsSel = selection.replace(FIELD_ID, FIELD_SESSION);
                mSQLiteDatabase.delete(TABLE_TRACKPOINTS, trackpointsSel, selectionArgs);
                break;
            case TYPE_SINGLE_TRACK:
            case TYPE_TRACKPOINTS:
                throw new IllegalArgumentException(
                        "Only multiple tracks deletion implemented (WHERE _id IN (?,...,?))");
            default:
                throw new IllegalArgumentException("Wrong tracks URI: " + uri);
        }

        int deleted = mSQLiteDatabase.delete(TABLE_TRACKS, selection, selectionArgs);
        getContext().getContentResolver().notifyChange(uri, null);

        return deleted;
    }


    public int update(
            Uri uri,
            ContentValues values,
            String selection,
            String[] selectionArgs)
    {
        mSQLiteDatabase = mMap.getDatabase(false);
        int updated;

        switch (mUriMatcher.match(uri)) {
            case TYPE_SINGLE_TRACK:
                String id = uri.getLastPathSegment();

                if (TextUtils.isEmpty(selection)) {
                    selection = FIELD_ID + " = " + id;
                } else {
                    selection = selection + " AND " + FIELD_ID + " = " + id;
                }
                break;
            case TYPE_TRACKS:
                break;
            case TYPE_TRACKPOINTS:
                throw new IllegalArgumentException("Trackpoints can't be updated");
            default:
                throw new IllegalArgumentException("Wrong tracks URI: " + uri);
        }

        updated = mSQLiteDatabase.update(TABLE_TRACKS, values, selection, selectionArgs);
        getContext().getContentResolver().notifyChange(uri, null);

        return updated;
    }


    public String getType(Uri uri)
    {
        switch (mUriMatcher.match(uri)) {
            case TYPE_TRACKS:
                return CONTENT_TYPE;
            case TYPE_SINGLE_TRACK:
                return CONTENT_ITEM_TYPE;
            case TYPE_TRACKPOINTS:
                return CONTENT_TYPE_TRACKPOINTS;
        }

        return null;
    }

}