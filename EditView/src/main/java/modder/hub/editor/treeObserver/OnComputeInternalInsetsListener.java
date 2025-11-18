/*
* MH-TextEditor - An Advanced and optimized TextEditor for android
* Copyright 2025, developer-krushna
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are
* met:
*
*     * Redistributions of source code must retain the above copyright
* notice, this list of conditions and the following disclaimer.
*     * Redistributions in binary form must reproduce the above
* copyright notice, this list of conditions and the following disclaimer
* in the documentation and/or other materials provided with the
* distribution.
*     * Neither the name of developer-krushna nor the names of its
* contributors may be used to endorse or promote products derived from
* this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
* "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
* LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
* A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
* OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
* SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
* LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
* DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
* THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
* (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
* OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.


*     Please contact Krushna by email modder-hub@zohomail.in if you need
*     additional information or have any questions
*/

package modder.hub.editor.treeObserver;

import android.graphics.Region;
import android.inputmethodservice.InputMethodService;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Created by max on 2019/2/22.<br> */
public class OnComputeInternalInsetsListener implements InvocationHandler {

    private Region touchRegion = null;

    public Object getListener() {
        Object target = null;
        try {
            Class class1 = Class.forName("android.view.ViewTreeObserver$OnComputeInternalInsetsListener");
            target = Proxy.newProxyInstance(OnComputeInternalInsetsListener.class.getClassLoader(),
                    new Class[]{class1}, this);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return target;
    }

    public Region getTouchRegion() {
        return touchRegion;
    }

    public void setTouchRegion(Region touchRegion) {
        this.touchRegion = touchRegion;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        try {
            Field regionField = args[0].getClass()
                    .getDeclaredField("touchableRegion");
            regionField.setAccessible(true);
            Field insetField = args[0].getClass()
                    .getDeclaredField("mTouchableInsets");
            insetField.setAccessible(true);
            if (touchRegion != null) {
                Region region = (Region) regionField.get(args[0]);
                region.set(touchRegion);
                insetField.set(args[0], InputMethodService.Insets.TOUCHABLE_INSETS_REGION);
            } else {
                insetField.set(args[0], InputMethodService.Insets.TOUCHABLE_INSETS_FRAME);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
