import re, shutil, sys
root = sys.argv[1]
# 1) System font: Display + Mono families -> FontFamily.Default (the device's system font)
p = f"{root}/smali_classes4/com/phonetemp/app/ui/theme/TypeKt.smali"
s = open(p).read()
sysfont = """    sget-object v0, Landroidx/compose/ui/text/font/FontFamily;->Companion:Landroidx/compose/ui/text/font/FontFamily$Companion;

    invoke-virtual {v0}, Landroidx/compose/ui/text/font/FontFamily$Companion;->getDefault()Landroidx/compose/ui/text/font/SystemFontFamily;

    move-result-object v0

"""
for field in ("Display", "Mono"):
    tgt = f"    sput-object v0, Lcom/phonetemp/app/ui/theme/TypeKt;->{field}:Landroidx/compose/ui/text/font/FontFamily;"
    assert s.count(tgt) == 1, field
    s = s.replace(tgt, sysfont + tgt)
open(p, "w").write(s)

# 2) Ring: delegate the Canvas draw lambda to GradientRing.draw
p = f"{root}/smali_classes6/com/phonetemp/app/ui/components/ThermalRingKt.smali"
s = open(p).read()
sig = ".method private static final ThermalRing_PfoAEA0$lambda$12$lambda$8$lambda$7(Lcom/phonetemp/app/ui/theme/PtColors;Landroidx/compose/runtime/State;Landroidx/compose/runtime/State;Landroidx/compose/ui/graphics/drawscope/DrawScope;)Lkotlin/Unit;"
i = s.index(sig); j = s.index(".end method", i)
body = sig + """
    .locals 1

    const-string v0, "$this$Canvas"
    invoke-static {p3, v0}, Lkotlin/jvm/internal/Intrinsics;->checkNotNullParameter(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-static {p1}, Lcom/phonetemp/app/ui/components/ThermalRingKt;->ThermalRing_PfoAEA0$lambda$3(Landroidx/compose/runtime/State;)F
    move-result v0

    invoke-static {p3, p0, v0}, Lcom/phonetemp/app/ui/components/GradientRing;->draw(Landroidx/compose/ui/graphics/drawscope/DrawScope;Lcom/phonetemp/app/ui/theme/PtColors;F)V

    sget-object v0, Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;
    return-object v0
"""
s = s[:i] + body + s[j:]
open(p, "w").write(s)
# 3) Pulse Lab: put O-Haptics Studio (feature/, shipped as classes10.dex) first in the examples
#    column, with the same (view, still) arguments the other examples get.
p = f"{root}/smali_classes5/com/phonetemp/app/ui/screens/PulseLabScreenKt$PulseLabScreen$2$4.smali"
s = open(p).read()
toggle = "    invoke-static {v4, v7, v10, v0}, Lcom/phonetemp/app/ui/screens/PulseLabScreenKt;->access$ToggleExample(Landroid/view/View;ZLandroidx/compose/runtime/Composer;I)V"
assert s.count(toggle) == 1
studio = "    invoke-static {v4, v7, v10, v0}, Lcom/phonetemp/app/ohaptics/StudioKt;->OHapticsStudio(Landroid/view/View;ZLandroidx/compose/runtime/Composer;I)V\n\n"
s = s.replace(toggle, studio + toggle)
open(p, "w").write(s)

# 4) Charging power: route BatteryReading's watts through ChargePower (feature/), which corrects
#    current x voltage for dual-cell packs, and show its status line under the Power number.
p = f"{root}/smali_classes3/com/phonetemp/app/data/BatteryReading.smali"
s = open(p).read()
old = "    invoke-virtual {v15, v1, v2}, Lcom/phonetemp/app/data/Normalize;->estimateWatts(Ljava/lang/Float;Ljava/lang/Float;)Ljava/lang/Float;"
assert s.count(old) == 1
s = s.replace(old, "    invoke-static {v1, v2}, Lcom/phonetemp/app/power/ChargePower;->watts(Ljava/lang/Float;Ljava/lang/Float;)Ljava/lang/Float;")
open(p, "w").write(s)

p = f"{root}/smali_classes5/com/phonetemp/app/ui/screens/HomeScreenKt$PowerCard$1.smali"
s = open(p).read()
old = """    const-string v1, "estimated"

    invoke-static {v1, v14, v9, v15, v13}, Lcom/phonetemp/app/ui/components/HomePartsKt;->CardCaption(Ljava/lang/String;Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;II)V
"""
assert s.count(old) == 1
# $changed was 6 ("static literal"); a live caption must be compared, so pass 0, then restore v15 = 6.
new = """    invoke-static {}, Lcom/phonetemp/app/power/ChargePower;->caption()Ljava/lang/String;

    move-result-object v1

    const/4 v15, 0x0

    invoke-static {v1, v14, v9, v15, v13}, Lcom/phonetemp/app/ui/components/HomePartsKt;->CardCaption(Ljava/lang/String;Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;II)V

    const/4 v15, 0x6
"""
s = s.replace(old, new)
open(p, "w").write(s)

# 5) Pulse Lab Feedback grid: one system constant per distinct feel, every duplicate re-cut as its
#    own pattern (FeedbackGrid in feature/). Grid cell labels + click handler only.
p = f"{root}/smali_classes5/com/phonetemp/app/ui/screens/PulseLabScreenKt$PulseLabScreen$2$1$1.smali"
s = open(p).read()
for getter, hook in (("getLabel", "label"), ("getTechnical", "technical")):
    old = f"    invoke-virtual/range {{p1 .. p1}}, Lcom/phonetemp/app/data/haptics/FeedbackId;->{getter}()Ljava/lang/String;"
    assert s.count(old) == 1, getter
    s = s.replace(old, f"    invoke-static/range {{p1 .. p1}}, Lcom/phonetemp/app/ohaptics/FeedbackGrid;->{hook}(Lcom/phonetemp/app/data/haptics/FeedbackId;)Ljava/lang/String;")
old = """    .line 152
    invoke-static {p1}, Lcom/phonetemp/app/ui/screens/PulseLabScreenKt;->access$feedbackConstant(Lcom/phonetemp/app/data/haptics/FeedbackId;)I
"""
assert s.count(old) == 1
s = s.replace(old, """    invoke-static {p0, p1}, Lcom/phonetemp/app/ohaptics/FeedbackGrid;->play(Landroid/view/View;Lcom/phonetemp/app/data/haptics/FeedbackId;)Z

    move-result v0

    if-eqz v0, :system_constant

    sget-object v0, Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;

    return-object v0

    :system_constant
""" + old)
open(p, "w").write(s)

p = f"{root}/smali_classes5/com/phonetemp/app/ui/screens/PulseLabScreenKt.smali"
s = open(p).read()
old = '"The constants Android uses for its own controls. These follow your system haptic setting."'
assert s.count(old) == 1
s = s.replace(old, '"One of each system feel Android offers, plus distinct patterns where the system ones felt the same."')
open(p, "w").write(s)

# 6) Pulse Lab "Rise and fall" and "Drag threshold": replaced by the direct-manipulation versions in
#    feature/PulseExtras.kt, in the same slots and under the same conditions.
p = f"{root}/smali_classes5/com/phonetemp/app/ui/screens/PulseLabScreenKt$PulseLabScreen$2$4.smali"
s = open(p).read()
old = "    invoke-static/range {v5 .. v10}, Lcom/phonetemp/app/ui/screens/PulseLabScreenKt;->access$SequenceExample(Ljava/lang/String;Ljava/lang/String;ZLkotlin/jvm/functions/Function0;Landroidx/compose/runtime/Composer;I)V"
assert s.count(old) == 1
# v4 = the screen's View, v7 = still, v9 = composer, v0 = 0 ($changed)
s = s.replace(old, "    invoke-static {v4, v7, v9, v0}, Lcom/phonetemp/app/ohaptics/PulseExtrasKt;->RiseFallExample(Landroid/view/View;ZLandroidx/compose/runtime/Composer;I)V")
old = "    invoke-static {v4, v5, v0}, Lcom/phonetemp/app/ui/screens/PulseLabScreenKt;->access$DragThresholdExample(Landroid/view/View;Landroidx/compose/runtime/Composer;I)V"
assert s.count(old) == 1
s = s.replace(old, "    invoke-static {v4, v5, v0}, Lcom/phonetemp/app/ohaptics/PulseExtrasKt;->DragThresholdPad(Landroid/view/View;Landroidx/compose/runtime/Composer;I)V")
open(p, "w").write(s)

import os; shutil.copy(os.path.join(os.path.dirname(__file__), "GradientRing.smali"), f"{root}/smali_classes6/com/phonetemp/app/ui/components/GradientRing.smali")
print("patched")
