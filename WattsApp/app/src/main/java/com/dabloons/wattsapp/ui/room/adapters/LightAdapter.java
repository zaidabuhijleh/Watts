package com.dabloons.wattsapp.ui.room.adapters;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.dabloons.wattsapp.R;
import com.dabloons.wattsapp.WattsApplication;
import com.dabloons.wattsapp.manager.LightManager;
import com.dabloons.wattsapp.model.Light;
import com.dabloons.wattsapp.model.LightState;
import com.dabloons.wattsapp.ui.room.RoomActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.skydoves.colorpickerview.ColorEnvelope;
import com.skydoves.colorpickerview.ColorPickerView;
import com.skydoves.colorpickerview.listeners.ColorListener;

import java.io.IOException;
import java.util.ArrayList;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import util.UIMessageUtil;
import util.WattsCallback;
import util.WattsCallbackStatus;

public class LightAdapter extends RecyclerView.Adapter<LightAdapter.Viewholder>
{
    private final String LOG_TAG = "LightAdapter";

    private Context context;
    public ArrayList<Light> lightModelArrayList;

    private MaterialAlertDialogBuilder alertDialogBuilder;
    private View customDialogView;

    private LightManager lightManager = LightManager.getInstance();

    private ColorPickerView colorPickerView;
    private float[] hsv;

    public LightAdapter(Context context, ArrayList<Light> lightModelArrayList) {
        this.context = context;
        this.lightModelArrayList = lightModelArrayList;

    }

    @NonNull
    @Override
    public Viewholder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.light_card_layout, parent, false);
        return new Viewholder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Viewholder holder, int position)
    {
        Light light = lightModelArrayList.get(position);
        holder.lightName.setText(light.getName());

        holder.lightSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if(isChecked) {
                lightManager.turnOnLight(light, (var, status) -> {
                    if(!status.success) {
                        Log.e(LOG_TAG, status.message);

                        UIMessageUtil.showShortToastMessage(buttonView.getContext(), "Failed to turn on light: " + light.getName());
                        return null;
                    }

                    UIMessageUtil.showShortToastMessage(buttonView.getContext(), "Turned on light: " + light.getName());
                    return null;
                });
            }
            else
            {
                lightManager.turnOffLight(light, (var, status) -> {
                    if(!status.success) {
                        Log.e(LOG_TAG, status.message);

                        UIMessageUtil.showShortToastMessage(buttonView.getContext(), "Failed to turn off light: " + light.getName());
                        return null;
                    }

                    UIMessageUtil.showShortToastMessage(buttonView.getContext(), "Turned off light: " + light.getName());
                    return null;
                });
            }
        });
//        holder.itemView.setOnTouchListener(new View.OnTouchListener() {
//            @Override
//            public boolean onTouch(View v, MotionEvent event) {
//
//                alertDialogBuilder = new MaterialAlertDialogBuilder(v.getContext());
//
//                customDialogView = LayoutInflater.from(WattsApplication.getAppContext()).inflate(R.layout.light_detail_dialog, null, false);
//                alertDialogBuilder.setView(customDialogView);
//                colorPickerView = customDialogView.findViewById(R.id.colorPickerView);
//                hsv = new float[3];
//
//                colorPickerView.setColorListener(new ColorListener() {
//                    @Override
//                    public void onColorSelected(int color, boolean fromUser) {
//                        ColorEnvelope colorEnvelope = new ColorEnvelope(color);
//                        int[] rgb = colorEnvelope.getArgb();
//
//                        Color c = new Color();
//                        c.RGBToHSV(rgb[0], rgb[1], rgb[2], hsv);
//
//                    }
//                });
//
//                alertDialogBuilder.setPositiveButton("Set", (dialog, which) ->
//                {
//                    Light light = lightModelArrayList.get(position);
//                    LightState lightState = new LightState(true, 100, hsv);
//                    LightManager.getInstance().setLightState(light, lightState, new WattsCallback<Void, Void>() {
//                        @Override
//                        public Void apply(Void var, WattsCallbackStatus status) {
//                            System.out.println("");
//                            return null;
//                        }
//                    });
//                });
//                return false;
//            }
//        });
    }

    @Override
    public int getItemCount() {
        return lightModelArrayList.size();
    }

    public class Viewholder extends RecyclerView.ViewHolder implements View.OnTouchListener  {
        private TextView lightName;
        private SwitchMaterial lightSwitch;

        public Viewholder(@NonNull View itemView) {
            super(itemView);
            lightName = itemView.findViewById(R.id.lightName);
            lightSwitch = itemView.findViewById(R.id.lightSwitch);
            itemView.setOnTouchListener(this);
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {

            alertDialogBuilder = new MaterialAlertDialogBuilder(v.getContext());

            customDialogView = LayoutInflater.from(WattsApplication.getAppContext()).inflate(R.layout.light_detail_dialog, null, false);
            alertDialogBuilder.setView(customDialogView);
            colorPickerView = customDialogView.findViewById(R.id.colorPickerView);
            hsv = new float[3];

            colorPickerView.setColorListener(new ColorListener() {
                @Override
                public void onColorSelected(int color, boolean fromUser) {
                    ColorEnvelope colorEnvelope = new ColorEnvelope(color);
                    int[] rgb = colorEnvelope.getArgb();

                    Color c = new Color();
                    c.RGBToHSV(rgb[1], rgb[2], rgb[3], hsv);

                }
            });

            alertDialogBuilder.setPositiveButton("Set", (dialog, which) ->
            {
                Light light = lightModelArrayList.get(this.getAbsoluteAdapterPosition());
                LightState lightState = new LightState(true, 100, hsv);
                LightManager.getInstance().setLightState(light, lightState, new WattsCallback<Void, Void>() {
                    @Override
                    public Void apply(Void var, WattsCallbackStatus status) {
                        System.out.println("");
                        return null;
                    }
                });
            });

            alertDialogBuilder.show();

            return false;
        }
    }
}
