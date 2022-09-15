package com.dabloons.wattsapp.manager;

import android.util.Log;

import androidx.annotation.NonNull;

import com.dabloons.wattsapp.WattsApplication;
import com.dabloons.wattsapp.model.Light;
import com.dabloons.wattsapp.model.LightState;
import com.dabloons.wattsapp.model.Room;
import com.dabloons.wattsapp.model.integration.IntegrationType;
import com.dabloons.wattsapp.repository.RoomRepository;
import com.dabloons.wattsapp.repository.UserRepository;
import com.dabloons.wattsapp.service.NanoleafService;
import com.dabloons.wattsapp.service.PhillipsHueService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.ResponseBody;
import util.UIMessageUtil;
import util.WattsCallback;
import util.WattsCallbackStatus;

public class RoomManager
{
    private final String LOG_TAG = "RoomManager";

    private static volatile RoomManager instance;

    private RoomRepository roomRepository = RoomRepository.getInstance();
    private PhillipsHueService phillipsHueService = PhillipsHueService.getInstance();
    private NanoleafService nanoleafService = NanoleafService.getInstance();
    private LightManager lightManager = LightManager.getInstance();

    private UserManager userManager = UserManager.getInstance();

    private ConcurrentLinkedQueue<IntegrationType> integrationsToResolve;

    private RoomManager()
    {
        integrationsToResolve = new ConcurrentLinkedQueue<>();
    }

    public static RoomManager getInstance() {
        RoomManager result = instance;
        if (result != null) {
            return result;
        }
        synchronized(UserRepository.class) {
            if (instance == null) {
                instance = new RoomManager();
            }
            return instance;
        }
    }

    public void createRoom(String roomName, WattsCallback<Room, Void> callback)
    {
        roomRepository.createRoom(roomName, callback);
    }

    public void addLightsToRoom(Room room, List<Light> lights, WattsCallback<Void, Void> callback) {
        if(lights.size() == 0)
        {
            callback.apply(null, new WattsCallbackStatus(true));
            return;
        }

        List<IntegrationType> integrationsUsed = integrationsUsedInLights(lights);
        List<String> lightIds = lights.stream().map(Light::getUid).collect(Collectors.toList());
        roomRepository.setRoomLights(room, lightIds).addOnCompleteListener(task -> {
            // Lights have been added to room in DB
            if(integrationsUsed.contains(IntegrationType.PHILLIPS_HUE))
                createPhillipsHueGroup(room, callback);
            else
                callback.apply(null, new WattsCallbackStatus(true));
        })
        .addOnFailureListener(task -> {
            callback.apply(null, new WattsCallbackStatus(false, task.getMessage()));
        });
    }

    public void turnOnRoomLights(Room room, WattsCallback<Void, Void> callback) {
        LightState state = new LightState(true, 1.0f);
        setRoomLightState(room, state, callback);
    }

    public void turnOffRoomLights(Room room, WattsCallback<Void, Void> callback) {
        LightState state = new LightState(false, 0.0f);
        setRoomLightState(room, state, callback);
    }

    public void getRoomForId(String roomId, WattsCallback<Room, Void> callback) {
        roomRepository.getUserDefinedRooms((rooms, status) -> {
            if(!status.success) {
                callback.apply(null, new WattsCallbackStatus(false, status.message));
                return null;
            }
            for(Room r : rooms) {
                if(r.getUid().equals(roomId)) {
                    callback.apply(r, new WattsCallbackStatus(true));
                    return null;
                }
            }
            callback.apply(null, new WattsCallbackStatus(false, "No room with id was found: " + roomId));
            return null;
        });
    }

    public void removeLightFromRoom(Room room, Light light, WattsCallback<Void, Void> callback) {
        List<String> lightIds = room.getLightIds();
        lightIds.remove(light.getUid());

        if(light.getIntegrationType() == IntegrationType.PHILLIPS_HUE) {
            phillipsHueService.setGroupLights(room, new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    callback.apply(null, new WattsCallbackStatus(false, e.getMessage()));
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if(!response.isSuccessful()) {
                        callback.apply(null, new WattsCallbackStatus(false, response.message()));
                        return;
                    }

                    setRoomRepositoryLights(room, callback);
                }
            });
        }

        setRoomRepositoryLights(room, callback);
    }

    public void getRoomIntegrationTypes(Room room, WattsCallback<List<IntegrationType>, Void> callback) {
        lightManager.getLightsForIds(room.getLightIds(), (lights, status) -> {
            List<IntegrationType> ret = new ArrayList<>();
            for(Light light : lights) {
                if(!ret.contains(light.getIntegrationType()))
                    ret.add(light.getIntegrationType());
            }

            callback.apply(ret, new WattsCallbackStatus(true));
            return null;
        });
    }

    public void getRoomLightsOfIntegration(Room room, IntegrationType type, WattsCallback<List<Light>, Void> callback) {
        lightManager.getLightsForIds(room.getLightIds(), (lights, status) -> {
            List<Light> ret = new ArrayList<>();
            for(Light light : lights) {
                if(light.getIntegrationType() == type)
                    ret.add(light);
            }

            callback.apply(ret, new WattsCallbackStatus(true));
            return null;
        });
    }

    public void deleteRoom(Room room, WattsCallback<Void, Void> callback) {
        roomRepository.deleteRoom(room.getUid()).addOnCompleteListener(task -> {
            if(!task.isComplete())
                callback.apply(null, new WattsCallbackStatus(false, "Failed to delete room"));

            integrationsUsedInLights(room.getLightIds(), (integrationsUsed, status) -> {
                if(integrationsUsed.contains(IntegrationType.PHILLIPS_HUE)) {
                    phillipsHueService.deleteGroup(room, new Callback() {
                        @Override
                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                            callback.apply(null, new WattsCallbackStatus(false, e.getMessage()));
                        }

                        @Override
                        public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                            if(response.isSuccessful())
                                callback.apply(null, new WattsCallbackStatus(true));
                            else
                                callback.apply(null, new WattsCallbackStatus(false, response.message()));
                        }
                    });
                } else {
                    callback.apply(null, new WattsCallbackStatus(true));
                }
                return null;
            });
        })
        .addOnFailureListener(task -> {
            callback.apply(null, new WattsCallbackStatus(false, task.getMessage()));
        });
    }

    public void deleteRoomsForUser(WattsCallback<Void, Void> callback) {
        roomRepository.deleteRoomsForUser(callback);
    }

    /*
     * HELPERS
     */

    private void createPhillipsHueGroup(Room room, WattsCallback<Void, Void> callback) {
        PhillipsHueService.getInstance().createGroupWithLights(room, new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.apply(null, new WattsCallbackStatus(false, e.getMessage()));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseData = response.body().string();
                JsonArray arr = JsonParser.parseString(responseData).getAsJsonArray();
                JsonObject successObj = arr.get(0).getAsJsonObject();
                try {
                    String integrationId = successObj.get("success")
                            .getAsJsonObject().get("id").getAsString();
                    room.setIntegrationId(integrationId);
                    roomRepository.setRoomIntegrationId(room.getUid(), integrationId); // may need to do onSuccessListener
                    callback.apply(null, new WattsCallbackStatus(true));
                } catch (Exception e) {
                    callback.apply(null, new WattsCallbackStatus(false, e.getMessage()));
                }
            }
        });
    }

    private void setRoomLightState(Room room, LightState state, WattsCallback<Void, Void> callback) {
        userManager.getUserIntegrations((integrations, status) -> {
            setIntegrationsToResolve(integrations);
            for(IntegrationType integration : integrations) {
                switch (integration) {
                    case PHILLIPS_HUE:
                        setPhillipsHueRoomLightState(room, state, (var, status1) -> {
                            resolveIntegration(integration, callback);
                            return null;
                        });
                        break;
                    case NANOLEAF:
                        setNanoleafRoomLightState(room, state, (var, status1) -> {
                            resolveIntegration(integration, callback);
                            return null;
                        });
                        break;
                }
            }

            return null;
        });

        setRoomLightStateInDB(room, state, (var, status) -> {
            if(!status.success) {
                Log.e(LOG_TAG, status.message);
                UIMessageUtil.showShortToastMessage(WattsApplication.getAppContext(), "Failed to set room lights in db");
            }

            return null;
        });
    }

    private void setRoomRepositoryLights(Room room, WattsCallback<Void, Void> callback) {
        roomRepository.setRoomLights(room, room.getLightIds())
                .addOnCompleteListener(task -> {
                    callback.apply(null, new WattsCallbackStatus(true));
                })
                .addOnFailureListener(task -> {
                    callback.apply(null, new WattsCallbackStatus(false, task.getMessage()));
                });
    }

    public void setRoomLightStateInDB(Room room, LightState state, WattsCallback<Void, Void> callback) {
        lightManager.getLightsForIds(room.getLightIds(), (lights, status) -> {
            updateLightStatesForLights(lights, state);
            lightManager.updateMultipleLights(lights, callback);
            return null;
        });
    }

    private void updateLightStatesForLights(List<Light> lights, LightState state) {
        for(Light l : lights) {
            LightState ls = l.getLightState();
            ls.setOn(state.isOn());
            ls.setBrightness(state.getBrightness());
            if(state.getHue() != null)
                ls.setHue(state.getHue());
            if(state.getSaturation() != null)
                ls.setSaturation(state.getSaturation());
        }
    }

    private void setPhillipsHueRoomLightState(Room room, LightState state, WattsCallback<Void, Void> callback) {
        phillipsHueService.setRoomLightsState(room, state, new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.apply(null, new WattsCallbackStatus(false, "Failed to set hue group light state"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                callback.apply(null, new WattsCallbackStatus(true));
            }
        });
    }

    private void setNanoleafRoomLightState(Room room, LightState state, WattsCallback<Void, Void> callback) {
        getRoomLightsOfIntegration(room, IntegrationType.NANOLEAF, (nanoleafs, status) -> {
            ConcurrentLinkedQueue<Light> remaining = new ConcurrentLinkedQueue<>(nanoleafs);
            setEachNanoleafLightState(remaining, state, callback);
            return null;
        });
    }

    private void setEachNanoleafLightState(ConcurrentLinkedQueue<Light> remaining, LightState state, WattsCallback<Void, Void> callback) {
        if(remaining.size() == 0) {
            callback.apply(null, new WattsCallbackStatus(true));
            return;
        }

        Light l = remaining.poll();
        nanoleafService.setLightState(l, state, new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.apply(null, new WattsCallbackStatus(false, e.getMessage()));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if(remaining.size() == 0) {
                    callback.apply(null, new WattsCallbackStatus(true));
                    return;
                }

                setEachNanoleafLightState(new ConcurrentLinkedQueue<>(remaining), state, callback);
            }
        });
    }

    private void integrationsUsedInLights(List<String> lightIds, WattsCallback<List<IntegrationType>, Void> callback) {
        lightManager.getLightsForIds(lightIds, (lights, status) -> {

            List<IntegrationType> ret = new ArrayList<>();
            for(Light l : lights)
                if(!ret.contains(l.getIntegrationType()))
                    ret.add(l.getIntegrationType());

            callback.apply(ret, new WattsCallbackStatus(true));
            return null;
        });
    }

    private List<IntegrationType> integrationsUsedInLights(List<Light> lights) {
        List<IntegrationType> ret = new ArrayList<>();
        for(Light l : lights)
            if(!ret.contains(l.getIntegrationType()))
                ret.add(l.getIntegrationType());
        return ret;
    }

    private void setIntegrationsToResolve(List<IntegrationType> integrations) {
        this.integrationsToResolve = new ConcurrentLinkedQueue<>(integrations);
    }

    private void resolveIntegration(IntegrationType integration, WattsCallback<Void, Void> onAllResolvedCallback) {
        if(this.integrationsToResolve.contains(integration)) {
            this.integrationsToResolve.remove(integration);
        }

        if(this.integrationsToResolve.size() == 0)
            onAllResolvedCallback.apply(null, new WattsCallbackStatus(true));
    }

    private void resetIntegrationsToResolve() {
        this.integrationsToResolve.clear();
    }

    /**
     * Debug only
     */
    public void getAllPhillipsHueGroups() {
        phillipsHueService.getAllGroups(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                System.out.println();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                ResponseBody responseData = response.body();
                String responseDataStr = new String(responseData.bytes());
                JsonObject jsonObj = JsonParser.parseString(responseDataStr).getAsJsonObject();
                System.out.println();
            }
        });
    }
}
