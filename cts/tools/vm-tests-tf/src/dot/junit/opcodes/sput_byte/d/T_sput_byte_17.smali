# Copyright (C) 2008 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

.source "T_sput_byte_17.java"
.class  public Ldot/junit/opcodes/sput_byte/d/T_sput_byte_17;
.super  Ljava/lang/Object;

.field public static st_i1:J

.method public constructor <init>()V
.registers 1

       invoke-direct {v0}, Ljava/lang/Object;-><init>()V
       return-void
.end method

.method public run()V
.registers 3

       const v1, 1
       sput-byte v1, Ldot/junit/opcodes/sput_byte/d/T_sput_byte_17;->st_i1:B

       return-void
.end method

