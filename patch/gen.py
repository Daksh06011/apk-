import struct
def fb(x): return hex(struct.unpack('>I',struct.pack('>f',x))[0])
ARC = 300/360
# (fraction of the 300° arc, PtColors getter) -> colour ramps with temperature
ramp = [(0.00,'Cool'),(0.10,'Cool'),(0.25,'Normal'),(0.62,'Normal'),(0.76,'Warm'),(0.87,'Hot'),(1.00,'Critical')]
stops = [(f*ARC,c) for f,c in ramp] + [(0.93,'Critical'),(0.97,'Cool'),(1.0,'Cool')]
b = []
for i,(off,c) in enumerate(stops):
    b.append(f"""    invoke-virtual {{p0}}, Lcom/phonetemp/app/ui/theme/PtColors;->getState{c}-0d7_KjU()J
    move-result-wide v2
    const v4, {fb(off)}    # {off:.4f}f
    invoke-static {{v4, v2, v3}}, Lcom/phonetemp/app/ui/components/GradientRing;->stop(FJ)Lkotlin/Pair;
    move-result-object v5
    const/16 v0, {hex(i)}
    aput-object v5, v1, v0
""")
brush = "\n".join(b)
DS="Landroidx/compose/ui/graphics/drawscope/DrawScope;"
src = f""".class public final Lcom/phonetemp/app/ui/components/GradientRing;
.super Ljava/lang/Object;
.source "GradientRing.kt"

# Draws the thermal ring with a temperature colour ramp (cool -> normal -> warm -> hot -> critical).
# The canvas is rotated so the arc starts at 0 deg, letting a sweep gradient map 1:1 onto the arc:
# as the fill grows its leading edge shifts colour with the temperature.

.method private static stop(FJ)Lkotlin/Pair;
    .locals 3
    invoke-static {{p0}}, Ljava/lang/Float;->valueOf(F)Ljava/lang/Float;
    move-result-object v0
    invoke-static {{p1, p2}}, Landroidx/compose/ui/graphics/Color;->box-impl(J)Landroidx/compose/ui/graphics/Color;
    move-result-object v1
    new-instance v2, Lkotlin/Pair;
    invoke-direct {{v2, v0, v1}}, Lkotlin/Pair;-><init>(Ljava/lang/Object;Ljava/lang/Object;)V
    return-object v2
.end method

.method private static stroke(F)Landroidx/compose/ui/graphics/drawscope/DrawStyle;
    .locals 8
    new-instance v0, Landroidx/compose/ui/graphics/drawscope/Stroke;
    sget-object v1, Landroidx/compose/ui/graphics/StrokeCap;->Companion:Landroidx/compose/ui/graphics/StrokeCap$Companion;
    invoke-virtual {{v1}}, Landroidx/compose/ui/graphics/StrokeCap$Companion;->getRound-KaPHkGw()I
    move-result v3
    move v1, p0
    const/4 v2, 0x0
    const/4 v4, 0x0
    const/4 v5, 0x0
    const/16 v6, 0x1a
    const/4 v7, 0x0
    invoke-direct/range {{v0 .. v7}}, Landroidx/compose/ui/graphics/drawscope/Stroke;-><init>(FFIILandroidx/compose/ui/graphics/PathEffect;ILkotlin/jvm/internal/DefaultConstructorMarker;)V
    return-object v0
.end method

.method private static brush(Lcom/phonetemp/app/ui/theme/PtColors;)Landroidx/compose/ui/graphics/Brush;
    .locals 6
    const/16 v0, {hex(len(stops))}
    new-array v1, v0, [Lkotlin/Pair;
{brush}
    sget-object v0, Landroidx/compose/ui/graphics/Brush;->Companion:Landroidx/compose/ui/graphics/Brush$Companion;
    const-wide/16 v2, 0x0
    const/4 v4, 0x2
    const/4 v5, 0x0
    invoke-static/range {{v0 .. v5}}, Landroidx/compose/ui/graphics/Brush$Companion;->sweepGradient-Uv8p0NA$default(Landroidx/compose/ui/graphics/Brush$Companion;[Lkotlin/Pair;JILjava/lang/Object;)Landroidx/compose/ui/graphics/Brush;
    move-result-object v0
    return-object v0
.end method

.method public static draw({DS}Lcom/phonetemp/app/ui/theme/PtColors;F)V
    .locals 30

    # stroke = 13.dp, inset = 9.dp + stroke / 2
    move-object/from16 v0, p0
    const/high16 v1, 0x41500000
    invoke-static {{v1}}, Landroidx/compose/ui/unit/Dp;->constructor-impl(F)F
    move-result v1
    invoke-interface {{v0, v1}}, {DS}->toPx-0680j_4(F)F
    move-result v1
    move/from16 v23, v1
    const/high16 v1, 0x41100000
    invoke-static {{v1}}, Landroidx/compose/ui/unit/Dp;->constructor-impl(F)F
    move-result v1
    invoke-interface {{v0, v1}}, {DS}->toPx-0680j_4(F)F
    move-result v1
    const/high16 v2, 0x40000000
    div-float v2, v23, v2
    add-float v24, v1, v2

    # arcSize (v25) and topLeft (v27)
    invoke-interface {{v0}}, {DS}->getSize-NH-jbRc()J
    move-result-wide v2
    invoke-static {{v2, v3}}, Landroidx/compose/ui/geometry/Size;->getWidth-impl(J)F
    move-result v4
    invoke-static {{v2, v3}}, Landroidx/compose/ui/geometry/Size;->getHeight-impl(J)F
    move-result v5
    add-float v6, v24, v24
    sub-float/2addr v4, v6
    sub-float/2addr v5, v6
    invoke-static {{v4, v5}}, Landroidx/compose/ui/geometry/SizeKt;->Size(FF)J
    move-result-wide v25
    move/from16 v4, v24
    invoke-static {{v4, v4}}, Landroidx/compose/ui/geometry/OffsetKt;->Offset(FF)J
    move-result-wide v27

    # rotate 120deg about the centre so the arc begins at angle 0
    invoke-interface {{v0}}, {DS}->getCenter-F1C5BW0()J
    move-result-wide v18
    invoke-interface {{v0}}, {DS}->getDrawContext()Landroidx/compose/ui/graphics/drawscope/DrawContext;
    move-result-object v1
    invoke-interface {{v1}}, Landroidx/compose/ui/graphics/drawscope/DrawContext;->getTransform()Landroidx/compose/ui/graphics/drawscope/DrawTransform;
    move-result-object v1
    move-object/from16 v20, v1
    move-wide/from16 v2, v18
    const/high16 v4, 0x42f00000
    invoke-interface {{v1, v4, v2, v3}}, Landroidx/compose/ui/graphics/drawscope/DrawTransform;->rotate-Uv8p0NA(FJ)V

    # track
    move/from16 v0, v23
    invoke-static {{v0}}, Lcom/phonetemp/app/ui/components/GradientRing;->stroke(F)Landroidx/compose/ui/graphics/drawscope/DrawStyle;
    move-result-object v11
    move-object/from16 v0, p1
    invoke-virtual {{v0}}, Lcom/phonetemp/app/ui/theme/PtColors;->getTrack-0d7_KjU()J
    move-result-wide v1
    move-object/from16 v0, p0
    const/4 v3, 0x0
    const/high16 v4, 0x43960000
    const/4 v5, 0x0
    move-wide/from16 v6, v27
    move-wide/from16 v8, v25
    const/high16 v10, 0x3f800000
    const/4 v12, 0x0
    const/4 v13, 0x0
    const/16 v14, 0x300
    const/4 v15, 0x0
    invoke-static/range {{v0 .. v15}}, {DS}->drawArc-yD3GUKo$default({DS}JFFZJJFLandroidx/compose/ui/graphics/drawscope/DrawStyle;Landroidx/compose/ui/graphics/ColorFilter;IILjava/lang/Object;)V

    move/from16 v0, p2
    const/4 v1, 0x0
    cmpl-float v0, v0, v1
    if-lez v0, :restore

    move-object/from16 v0, p1
    invoke-static {{v0}}, Lcom/phonetemp/app/ui/components/GradientRing;->brush(Lcom/phonetemp/app/ui/theme/PtColors;)Landroidx/compose/ui/graphics/Brush;
    move-result-object v1
    move-object/from16 v21, v1

    # soft halo under the fill
    move/from16 v0, v23
    const/high16 v2, 0x40000000
    mul-float/2addr v0, v2
    invoke-static {{v0}}, Lcom/phonetemp/app/ui/components/GradientRing;->stroke(F)Landroidx/compose/ui/graphics/drawscope/DrawStyle;
    move-result-object v10
    move-object/from16 v0, p0
    move-object/from16 v1, v21
    const/4 v2, 0x0
    move/from16 v3, p2
    const/4 v4, 0x0
    move-wide/from16 v5, v27
    move-wide/from16 v7, v25
    const v9, 0x3e0f5c29
    const/4 v11, 0x0
    const/4 v12, 0x0
    const/16 v13, 0x300
    const/4 v14, 0x0
    invoke-static/range {{v0 .. v14}}, {DS}->drawArc-illE91I$default({DS}Landroidx/compose/ui/graphics/Brush;FFZJJFLandroidx/compose/ui/graphics/drawscope/DrawStyle;Landroidx/compose/ui/graphics/ColorFilter;IILjava/lang/Object;)V

    # gradient fill
    move/from16 v0, v23
    invoke-static {{v0}}, Lcom/phonetemp/app/ui/components/GradientRing;->stroke(F)Landroidx/compose/ui/graphics/drawscope/DrawStyle;
    move-result-object v10
    move-object/from16 v0, p0
    move-object/from16 v1, v21
    const/4 v2, 0x0
    move/from16 v3, p2
    const/4 v4, 0x0
    move-wide/from16 v5, v27
    move-wide/from16 v7, v25
    const/high16 v9, 0x3f800000
    const/4 v11, 0x0
    const/4 v12, 0x0
    const/16 v13, 0x300
    const/4 v14, 0x0
    invoke-static/range {{v0 .. v14}}, {DS}->drawArc-illE91I$default({DS}Landroidx/compose/ui/graphics/Brush;FFZJJFLandroidx/compose/ui/graphics/drawscope/DrawStyle;Landroidx/compose/ui/graphics/ColorFilter;IILjava/lang/Object;)V

    :restore
    move-object/from16 v1, v20
    move-wide/from16 v2, v18
    const/high16 v0, -0x3d100000
    invoke-interface {{v1, v0, v2, v3}}, Landroidx/compose/ui/graphics/drawscope/DrawTransform;->rotate-Uv8p0NA(FJ)V
    return-void
.end method
"""
import os; open(os.path.join(os.path.dirname(__file__),'GradientRing.smali'),'w').write(src)
