package com.giorg.mtr_tweaks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.mtr.mod.screen.EditDepotScreen;

@Mixin(value = EditDepotScreen.class, remap = false)
public class EditDepotScreenMixin extends org.mtr.mod.screen.EditNameColorScreenBase<org.mtr.core.data.Depot> {

    @Unique
    private org.mtr.mapping.mapper.TextFieldWidgetExtension mtrTweaks_textFieldClimbPitch;
    @Unique
    private org.mtr.mapping.mapper.TextFieldWidgetExtension mtrTweaks_textFieldLandPitch;

    @Shadow(remap = false)
    private boolean showCruisingAltitude;

    @Shadow(remap = false)
    private org.mtr.mapping.holder.MutableText cruisingAltitudeText;

    @Shadow(remap = false)
    private int rightPanelsX;

    @Shadow(remap = false)
    private org.mtr.mapping.mapper.CheckboxWidgetExtension checkboxRepeatIndefinitely;

    // Dummy constructor to satisfy compiler since superclass has no default constructor
    public EditDepotScreenMixin() {
        super(null, null, null, null);
    }

    @Inject(method = "<init>(Lorg/mtr/core/data/Depot;Lorg/mtr/core/data/TransportMode;Lorg/mtr/mapping/mapper/ScreenExtension;)V", at = @At("TAIL"), remap = false)
    private void mtrTweaks_onInit(org.mtr.core.data.Depot depot, org.mtr.core.data.TransportMode transportMode, org.mtr.mapping.mapper.ScreenExtension previousScreen, CallbackInfo ci) {
        if (transportMode == org.mtr.core.data.TransportMode.AIRPLANE) {
            this.mtrTweaks_textFieldClimbPitch = new org.mtr.mapping.mapper.TextFieldWidgetExtension(0, 0, 0, 20, 5, org.mtr.mapping.tool.TextCase.DEFAULT, "[^-\\d.]", "15");
            this.mtrTweaks_textFieldLandPitch = new org.mtr.mapping.mapper.TextFieldWidgetExtension(0, 0, 0, 20, 5, org.mtr.mapping.tool.TextCase.DEFAULT, "[^-\\d.]", "-10");
        }
    }

    @Inject(method = "init2()V", at = @At("TAIL"), remap = false)
    private void mtrTweaks_init2(CallbackInfo ci) {
        if (this.showCruisingAltitude && this.mtrTweaks_textFieldClimbPitch != null) {
            int cruisingAltitudeTextWidth = org.mtr.mapping.mapper.GraphicsHolder.getTextWidth(this.cruisingAltitudeText) + 12;
            
            // Reflectively get screen width to avoid any mapping compile-time issues
            int screenWidth = 0;
            try {
                java.lang.reflect.Field f = net.minecraft.client.gui.screens.Screen.class.getField("width");
                screenWidth = f.getInt(this);
            } catch (Exception e) {
                try {
                    java.lang.reflect.Field f = net.minecraft.client.gui.screens.Screen.class.getDeclaredField("f_96543_");
                    f.setAccessible(true);
                    screenWidth = f.getInt(this);
                } catch (Exception ex) {
                    screenWidth = this.rightPanelsX * 4 / 3;
                }
            }

            int textWidth = Math.min(cruisingAltitudeTextWidth, (screenWidth - this.rightPanelsX) - 60);

            org.mtr.mod.client.IDrawing.setPositionAndWidth(this.mtrTweaks_textFieldClimbPitch, this.rightPanelsX + textWidth + 2, 130, 56);
            org.mtr.mod.client.IDrawing.setPositionAndWidth(this.mtrTweaks_textFieldLandPitch, this.rightPanelsX + textWidth + 2, 154, 56);

            String depotName = this.data.getName();
            this.mtrTweaks_textFieldClimbPitch.setText2(String.valueOf(com.giorg.mtr_tweaks.MTRTweaks.getClimbPitch(depotName)));
            this.mtrTweaks_textFieldLandPitch.setText2(String.valueOf(com.giorg.mtr_tweaks.MTRTweaks.getLandPitch(depotName)));

            // Shift checkboxRepeatIndefinitely down to y = 178
            int checkboxWidth = screenWidth - this.rightPanelsX;
            org.mtr.mod.client.IDrawing.setPositionAndWidth(this.checkboxRepeatIndefinitely, this.rightPanelsX, 178, checkboxWidth);

            this.addChild(new org.mtr.mapping.holder.ClickableWidget(this.mtrTweaks_textFieldClimbPitch));
            this.addChild(new org.mtr.mapping.holder.ClickableWidget(this.mtrTweaks_textFieldLandPitch));

            this.mtrTweaks_textFieldClimbPitch.setChangedListener2(text -> {
                try {
                    float climb = Float.parseFloat(text);
                    com.giorg.mtr_tweaks.MTRTweaks.depotPitches.put(depotName, new com.giorg.mtr_tweaks.MTRTweaks.PitchSettings(
                        climb,
                        com.giorg.mtr_tweaks.MTRTweaks.getLandPitch(depotName)
                    ));
                    com.giorg.mtr_tweaks.MTRTweaks.saveConfig();
                } catch (Exception e) {
                    // Ignore
                }
            });

            this.mtrTweaks_textFieldLandPitch.setChangedListener2(text -> {
                try {
                    float land = Float.parseFloat(text);
                    com.giorg.mtr_tweaks.MTRTweaks.depotPitches.put(depotName, new com.giorg.mtr_tweaks.MTRTweaks.PitchSettings(
                        com.giorg.mtr_tweaks.MTRTweaks.getClimbPitch(depotName),
                        land
                    ));
                    com.giorg.mtr_tweaks.MTRTweaks.saveConfig();
                } catch (Exception e) {
                    // Ignore
                }
            });
        }
    }

    @Inject(method = "tick2()V", at = @At("TAIL"), remap = false)
    private void mtrTweaks_tick2(CallbackInfo ci) {
        if (this.showCruisingAltitude && this.mtrTweaks_textFieldClimbPitch != null) {
            this.mtrTweaks_textFieldClimbPitch.tick2();
            this.mtrTweaks_textFieldLandPitch.tick2();
        }
    }

    @Inject(method = "render(Lorg/mtr/mapping/mapper/GraphicsHolder;IIF)V", at = @At("TAIL"), remap = false)
    private void mtrTweaks_render(org.mtr.mapping.mapper.GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.showCruisingAltitude && this.mtrTweaks_textFieldClimbPitch != null) {
            graphicsHolder.drawText(org.mtr.mapping.mapper.TextHelper.literal("Climb Pitch (deg)"), this.rightPanelsX + 6, 136, -1, false, org.mtr.mapping.mapper.GraphicsHolder.getDefaultLight());
            graphicsHolder.drawText(org.mtr.mapping.mapper.TextHelper.literal("Land Pitch (deg)"), this.rightPanelsX + 6, 160, -1, false, org.mtr.mapping.mapper.GraphicsHolder.getDefaultLight());
        }
    }

    @Redirect(
        method = "render(Lorg/mtr/mapping/mapper/GraphicsHolder;IIF)V",
        at = @At(
            value = "INVOKE",
            target = "Lorg/mtr/mapping/mapper/GraphicsHolder;drawText(Lorg/mtr/mapping/holder/MutableText;IIIZI)V"
        ),
        remap = false
    )
    private void mtrTweaks_redirectDrawTextMutable(org.mtr.mapping.mapper.GraphicsHolder graphicsHolder, org.mtr.mapping.holder.MutableText text, int x, int y, int color, boolean shadow, int light) {
        if (this.showCruisingAltitude && x >= this.rightPanelsX && y > 112) {
            y += 48;
        }
        graphicsHolder.drawText(text, x, y, color, shadow, light);
    }

    @Redirect(
        method = "render(Lorg/mtr/mapping/mapper/GraphicsHolder;IIF)V",
        at = @At(
            value = "INVOKE",
            target = "Lorg/mtr/mapping/mapper/GraphicsHolder;drawText(Ljava/lang/String;IIIZI)V"
        ),
        remap = false
    )
    private void mtrTweaks_redirectDrawTextString(org.mtr.mapping.mapper.GraphicsHolder graphicsHolder, java.lang.String text, int x, int y, int color, boolean shadow, int light) {
        if (this.showCruisingAltitude && x >= this.rightPanelsX && y > 112) {
            y += 48;
        }
        graphicsHolder.drawText(text, x, y, color, shadow, light);
    }

    @Inject(method = "saveData()V", at = @At("TAIL"), remap = false)
    private void mtrTweaks_saveData(CallbackInfo ci) {
        if (this.showCruisingAltitude && this.mtrTweaks_textFieldClimbPitch != null) {
            String depotName = this.data.getName();
            try {
                float climb = Float.parseFloat(this.mtrTweaks_textFieldClimbPitch.getText2());
                float land = Float.parseFloat(this.mtrTweaks_textFieldLandPitch.getText2());
                com.giorg.mtr_tweaks.MTRTweaks.depotPitches.put(depotName, new com.giorg.mtr_tweaks.MTRTweaks.PitchSettings(climb, land));
                com.giorg.mtr_tweaks.MTRTweaks.saveConfig();
            } catch (Exception e) {
                com.giorg.mtr_tweaks.MTRTweaks.LOGGER.error("Failed to save depot pitch settings", e);
            }
        }
    }
}
