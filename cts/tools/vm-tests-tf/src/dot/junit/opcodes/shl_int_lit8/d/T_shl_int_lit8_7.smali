.source "T_shl_int_lit8_7.java"
.class  public Ldot/junit/opcodes/shl_int_lit8/d/T_shl_int_lit8_7;
.super  Ljava/lang/Object;


.method public constructor <init>()V
.registers 1

       invoke-direct {v0}, Ljava/lang/Object;-><init>()V
       return-void
.end method

.method public run(I)I
.registers 8

       shl-int/lit8 v0, v8, 2
       return v0
.end method
