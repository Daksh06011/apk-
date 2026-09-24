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
import os; shutil.copy(os.path.join(os.path.dirname(__file__), "GradientRing.smali"), f"{root}/smali_classes6/com/phonetemp/app/ui/components/GradientRing.smali")
print("patched")
