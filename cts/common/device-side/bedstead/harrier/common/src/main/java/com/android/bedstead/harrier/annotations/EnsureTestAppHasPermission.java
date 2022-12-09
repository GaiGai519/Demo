/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.bedstead.harrier.annotations;

import static com.android.bedstead.harrier.annotations.EnsureTestAppInstalled.DEFAULT_TEST_APP_KEY;
import static com.android.bedstead.harrier.annotations.EnsureTestAppInstalled.ENSURE_TEST_APP_INSTALLED_WEIGHT;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Ensure that the given permission is granted to the test app before running the test.
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(EnsureTestAppHasPermissionGroup.class)
public @interface EnsureTestAppHasPermission {
    String[] value();

    String testAppKey() default DEFAULT_TEST_APP_KEY;

    /** The minimum version where this permission is required. */
    int minVersion() default 0;

    /** The maximum version where this permission is required. */
    int maxVersion() default Integer.MAX_VALUE;

    /**
     * Weight sets the order that annotations will be resolved.
     *
     * <p>Annotations with a lower weight will be resolved before annotations with a higher weight.
     *
     * <p>If there is an order requirement between annotations, ensure that the weight of the
     * annotation which must be resolved first is lower than the one which must be resolved later.
     *
     * <p>Weight can be set to a {@link AnnotationRunPrecedence} constant, or to any {@link int}.
     */
    int weight() default ENSURE_TEST_APP_INSTALLED_WEIGHT + 1;
}
