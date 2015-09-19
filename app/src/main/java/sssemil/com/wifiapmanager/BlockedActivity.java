/*
 * Copyright (C) 2015 Emil Suleymanov <suleymanovemil8@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sssemil.com.wifiapmanager;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import java.io.IOException;
import java.util.ArrayList;

import sssemil.com.wifiapmanager.Utils.ClientsList;
import sssemil.com.wifiapmanager.Utils.WifiApManager;

public class BlockedActivity extends AppCompatActivity {

    private ArrayList<String> mBlockedList;

    public static void restartAP(Context context) {
        final WifiApManager wifiApManager = new WifiApManager(context);
        new Thread(new Runnable() {
            @Override
            public void run() {
                wifiApManager.restartWifiAp();
            }
        }).start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocked);

        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Toast.makeText(this, R.string.touch_to_remove, Toast.LENGTH_SHORT).show();

        final ListView listView = (ListView) findViewById(R.id.list);
        final TextView nothing = (TextView) findViewById(R.id.nothing);

        try {
            mBlockedList = ClientsList.getDeniedMACList();
            final Adapter adapter = new Adapter(this, mBlockedList);

            listView.setAdapter(adapter);
            if (listView.getCount() == 0) {
                nothing.setVisibility(View.VISIBLE);
            } else {
                nothing.setVisibility(View.GONE);
            }
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, final View view,
                                        int position, long id) {
                    final String item = (String) parent.getItemAtPosition(position);
                    mBlockedList.remove(item);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                ClientsList.setDeniedMACList(mBlockedList);
                                restartAP(BlockedActivity.this);
                            } catch (IOException | InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }).start();
                    adapter.notifyDataSetChanged();
                    view.setAlpha(1);
                    if (listView.getCount() == 0) {
                        nothing.setVisibility(View.VISIBLE);
                    } else {
                        nothing.setVisibility(View.INVISIBLE);
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStart() {
        super.onStart();
        Tracker tracker = AnalyticsApplication.getDefaultTracker(this);
        Log.i(this.getLocalClassName(), "Setting screen name: " + this.getLocalClassName());
        tracker.setScreenName(this.getLocalClassName());
        tracker.send(new HitBuilders.ScreenViewBuilder().build());
    }

    private class Adapter extends ArrayAdapter<String> {
        private final Context context;
        private final ArrayList<String> values;

        public Adapter(Context context, ArrayList<String> values) {
            super(context, -1, values);
            this.context = context;
            this.values = values;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View rowView = inflater.inflate(R.layout.blocked_list_item, parent, false);
            TextView mac = (TextView) rowView.findViewById(R.id.mac);
            mac.setText(values.get(position));

            return rowView;
        }
    }
}
