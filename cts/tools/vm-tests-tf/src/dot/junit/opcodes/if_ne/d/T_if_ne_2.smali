.source "T_if_ne_2.java"
.class  public Ldot/junit/opcodes/if_ne/d/T_if_ne_2;
.super  Ljava/lang/Object;


.method public constructor <init>()V
.registers 1

       invoke-direct {v0}, Ljava/lang/Object;-><init>()V
       return-void
.end method

.method public run(Ljava/lang/String;Ljava/lang/String;)I
.registers 8

       if-ne v6, v7, :Label11
       const/16 v6, 1234
       return v6

:Label11
       const/4 v6, 1
       return v6
.end method
