.source "T_shr_long_2addr_7.java"
.class  public Ldot/junit/opcodes/shr_long_2addr/d/T_shr_long_2addr_7;
.super  Ljava/lang/Object;


.method public constructor <init>()V
.registers 1

       invoke-direct {v0}, Ljava/lang/Object;-><init>()V
       return-void
.end method

.method public run(DI)J
.registers 11

       shr-long/2addr v8, v10
       return-wide v8
.end method
