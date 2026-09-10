package com.webtoapp.core.webview

/**
 * Document-start helper for NativeBridge screen capture.
 *
 * Exposes `window.WtaScreenCapture`, a promise-based wrapper so web content
 * never hand-rolls the global-function callbacks the bridge requires:
 *
 * - `capture()` → one-shot WebView-content JPEG (base64)
 * - `requestDeviceAccess()` → consent dialog, resolves true/false
 * - `getDisplayMedia({source, quality, interval})` → a REAL MediaStream
 *   (frames are painted into an offscreen canvas and handed out via
 *   `canvas.captureStream()`), usable in `<video>` or WebRTC. `source` is
 *   `'device'`, `'webview'`, or `'auto'` (device with WebView fallback).
 * - `stopStream(stream)` / `stopAll()` → release native loops
 *
 * Deliberately does NOT patch `navigator.mediaDevices.getDisplayMedia`: a
 * fake MediaStreamTrack shim would mislead feature detection. Pages opt in
 * via `WtaScreenCapture` explicitly.
 *
 * Inject with [getInjectionScript] at document start (WebViewManager,
 * ShellActivity, FloatingWindowService mirror the PrintBridge wiring) gated
 * on `enableNativeBridge && nativeBridgeCapabilities.screenCapture`.
 */
object ScreenCaptureHelper {

    fun getInjectionScript(): String = INJECTION_SCRIPT

    private val INJECTION_SCRIPT = """
        (function() {
            'use strict';
            if (window.__webtoapp_screencapture_helper__) return;
            window.__webtoapp_screencapture_helper__ = true;

            var seq = 0;
            function uniqueFn(prefix) {
                seq += 1;
                return '__wta_sc_' + prefix + '_' + seq + '_' + Date.now().toString(36);
            }

            function bridge() {
                return (typeof NativeBridge !== 'undefined') ? NativeBridge : null;
            }

            function domError(name, message) {
                var err;
                try {
                    err = new DOMException(message, name);
                } catch (e) {
                    err = new Error(message);
                    err.name = name;
                }
                return err;
            }

            // Live frame sessions multiplexed onto the single native loop.
            var sessions = [];
            var nativeRunning = false;
            var nativeSource = null;

            function currentNativeStart(source, quality, interval) {
                var b = bridge();
                if (!b) return false;
                try {
                    if (source === 'device' && typeof b.startDeviceCapture === 'function') {
                        b.startDeviceCapture(quality, '__wta_sc_frame', interval);
                    } else if (typeof b.startScreenCapture === 'function') {
                        b.startScreenCapture(quality, '__wta_sc_frame', interval);
                    } else {
                        return false;
                    }
                    return true;
                } catch (e) {
                    return false;
                }
            }

            function stopNative() {
                var b = bridge();
                try {
                    if (b) {
                        if (typeof b.stopScreenCapture === 'function') b.stopScreenCapture();
                        if (typeof b.stopDeviceCapture === 'function') b.stopDeviceCapture();
                    }
                } catch (e) {}
                nativeRunning = false;
                nativeSource = null;
            }

            function ensureNativeLoop(source, quality, interval) {
                if (nativeRunning && nativeSource === source) return true;
                // Source switch: restart the loop for the newly requested source.
                stopNative();
                if (!currentNativeStart(source, quality, interval)) return false;
                nativeRunning = true;
                nativeSource = source;
                return true;
            }

            // Demultiplexer installed once; native invokes __wta_sc_frame(base64).
            window.__wta_sc_frame = function(data) {
                if (typeof data !== 'string' || !data) return;
                if (data === 'false' || data === 'revoked') {
                    var failed = sessions.slice();
                    sessions = [];
                    stopNative();
                    failed.forEach(function(s) {
                        try { s.onStreamError(data); } catch (e) {}
                    });
                    return;
                }
                sessions.slice().forEach(function(s) {
                    try { s.onFrame(data); } catch (e) {}
                });
            };

            function decodeToCanvas(dataUrl, canvas) {
                return new Promise(function(resolve, reject) {
                    var img = new Image();
                    img.onload = function() {
                        try {
                            if (canvas.width !== img.naturalWidth || canvas.height !== img.naturalHeight) {
                                canvas.width = img.naturalWidth;
                                canvas.height = img.naturalHeight;
                            }
                            var ctx = canvas.getContext('2d');
                            ctx.drawImage(img, 0, 0);
                            resolve();
                        } catch (e) {
                            reject(e);
                        }
                    };
                    img.onerror = function() { reject(new Error('frame decode failed')); };
                    img.src = dataUrl;
                });
            }

            function removeSession(session) {
                var i = sessions.indexOf(session);
                if (i >= 0) sessions.splice(i, 1);
                if (sessions.length === 0) stopNative();
            }

            var api = {
                isSupported: function() {
                    var b = bridge();
                    return !!(b && typeof b.captureScreen === 'function');
                },

                isDeviceCaptureAvailable: function() {
                    var b = bridge();
                    return !!(b && typeof b.isDeviceCaptureGranted === 'function');
                },

                isDeviceGranted: function() {
                    try {
                        var b = bridge();
                        return !!(b && typeof b.isDeviceCaptureGranted === 'function' && b.isDeviceCaptureGranted());
                    } catch (e) {
                        return false;
                    }
                },

                toDataUrl: function(base64) {
                    return 'data:image/jpeg;base64,' + base64;
                },

                capture: function(quality) {
                    return new Promise(function(resolve, reject) {
                        var b = bridge();
                        if (!b || typeof b.captureScreen !== 'function') {
                            reject(domError('NotSupportedError', 'screen capture bridge unavailable'));
                            return;
                        }
                        var q = (typeof quality === 'number') ? quality : 90;
                        var data;
                        try {
                            data = b.captureScreen(q);
                        } catch (e) {
                            reject(e);
                            return;
                        }
                        if (typeof data === 'string' && data) {
                            resolve(data);
                        } else {
                            reject(domError('NotAllowedError', 'capture failed or disabled'));
                        }
                    });
                },

                requestDeviceAccess: function(timeoutMs) {
                    return new Promise(function(resolve, reject) {
                        var b = bridge();
                        if (!b || typeof b.requestDeviceCapture !== 'function') {
                            reject(domError('NotSupportedError', 'device capture bridge unavailable'));
                            return;
                        }
                        var settled = false;
                        var fn = uniqueFn('grant');
                        var timer = null;
                        var timeout = (typeof timeoutMs === 'number') ? timeoutMs : 120000;
                        window[fn] = function(granted) {
                            if (settled) return;
                            settled = true;
                            if (timer) clearTimeout(timer);
                            try { delete window[fn]; } catch (e) { window[fn] = undefined; }
                            resolve(granted === true || granted === 'true');
                        };
                        if (timeout > 0) {
                            timer = setTimeout(function() {
                                if (settled) return;
                                settled = true;
                                try { delete window[fn]; } catch (e) { window[fn] = undefined; }
                                reject(domError('TimeoutError', 'capture consent timed out'));
                            }, timeout);
                        }
                        try {
                            b.requestDeviceCapture(fn);
                        } catch (e) {
                            if (!settled) {
                                settled = true;
                                if (timer) clearTimeout(timer);
                                reject(e);
                            }
                        }
                    });
                },

                getDisplayMedia: function(options) {
                    var opts = options || {};
                    var source = opts.source || 'auto';
                    var quality = (typeof opts.quality === 'number') ? opts.quality : 70;
                    var interval = (typeof opts.interval === 'number') ? opts.interval : 500;
                    var frameTimeout = (typeof opts.frameTimeout === 'number') ? opts.frameTimeout : 15000;

                    return new Promise(function(resolve, reject) {
                        var b = bridge();
                        if (!b) {
                            reject(domError('NotSupportedError', 'screen capture bridge unavailable'));
                            return;
                        }

                        function trySource(src) {
                            if (src === 'device' && typeof b.startDeviceCapture !== 'function') return false;
                            if (src === 'webview' && typeof b.startScreenCapture !== 'function') return false;
                            return ensureNativeLoop(src, quality, interval);
                        }

                        var effectiveSource = (source === 'webview') ? 'webview' : 'device';
                        if (!trySource(effectiveSource)) {
                            if (source === 'auto' && effectiveSource === 'device') {
                                effectiveSource = 'webview';
                                if (!trySource('webview')) {
                                    reject(domError('NotAllowedError', 'capture failed to start'));
                                    return;
                                }
                            } else {
                                reject(domError('NotAllowedError', 'capture failed to start'));
                                return;
                            }
                        }

                        var canvas = document.createElement('canvas');
                        canvas.width = 2;
                        canvas.height = 2;
                        canvas.style.display = 'none';
                        if (document.documentElement) {
                            document.documentElement.appendChild(canvas);
                        }

                        var settled = false;
                        var session = null;
                        var firstFrameTimer = null;
                        function removeCanvas() {
                            if (canvas.parentNode) canvas.parentNode.removeChild(canvas);
                        }
                        function cleanup() {
                            if (firstFrameTimer) clearTimeout(firstFrameTimer);
                            if (session) removeSession(session);
                            removeCanvas();
                        }
                        session = {
                            canvas: canvas,
                            stream: null,
                            onFrame: function(data) {
                                decodeToCanvas(api.toDataUrl(data), canvas).then(function() {
                                    if (settled) return;
                                    settled = true;
                                    if (firstFrameTimer) clearTimeout(firstFrameTimer);
                                    var stream = null;
                                    try {
                                        stream = canvas.captureStream();
                                    } catch (e) {
                                        cleanup();
                                        reject(e);
                                        return;
                                    }
                                    session.stream = stream;
                                    var track = stream.getVideoTracks()[0];
                                    if (track) {
                                        track.onended = function() { api.stopStream(stream); };
                                    }
                                    resolve(stream);
                                }).catch(function() {
                                    // A single corrupt frame must not kill the stream request;
                                    // the first-frame timeout below still guards a dead feed.
                                });
                            },
                            onStreamError: function(reason) {
                                if (settled) {
                                    // An established stream whose feed died: end its tracks.
                                    if (session.stream) {
                                        try {
                                            session.stream.getTracks().forEach(function(t) { t.stop(); });
                                        } catch (e) {}
                                    }
                                    removeSession(session);
                                    removeCanvas();
                                    return;
                                }
                                settled = true;
                                cleanup();
                                if (reason === 'revoked') {
                                    reject(domError('NotAllowedError', 'capture authorization revoked'));
                                } else if (source === 'auto' && effectiveSource === 'device') {
                                    // Transparent fallback: device denied/unsupported mid-handshake.
                                    effectiveSource = 'webview';
                                    settled = false;
                                    if (!trySource('webview')) {
                                        settled = true;
                                        cleanup();
                                        reject(domError('NotAllowedError', 'capture denied or unsupported'));
                                    }
                                    // Re-arm below by falling through: reset state and wait.
                                    if (!settled) {
                                        firstFrameTimer = setTimeout(onFirstFrameTimeout, frameTimeout);
                                    }
                                } else {
                                    reject(domError('NotAllowedError', 'capture denied or unsupported'));
                                }
                            }
                        };

                        function onFirstFrameTimeout() {
                            if (settled) return;
                            settled = true;
                            cleanup();
                            reject(domError('TimeoutError', 'no capture frame received'));
                        }
                        if (frameTimeout > 0) {
                            firstFrameTimer = setTimeout(onFirstFrameTimeout, frameTimeout);
                        }
                        sessions.push(session);
                    });
                },

                stopStream: function(stream) {
                    if (!stream) {
                        api.stopAll();
                        return;
                    }
                    for (var i = sessions.length - 1; i >= 0; i--) {
                        if (sessions[i].stream === stream) {
                            var s = sessions.splice(i, 1)[0];
                            try {
                                stream.getTracks().forEach(function(t) { try { t.stop(); } catch (e) {} });
                            } catch (e) {}
                            var node = s.canvas;
                            if (node && node.parentNode) node.parentNode.removeChild(node);
                        }
                    }
                    if (sessions.length === 0) stopNative();
                },

                stopAll: function() {
                    var live = sessions.slice();
                    sessions = [];
                    stopNative();
                    live.forEach(function(s) {
                        try {
                            if (s.stream) {
                                s.stream.getTracks().forEach(function(t) { try { t.stop(); } catch (e) {} });
                            }
                        } catch (e) {}
                        try {
                            var node = s.canvas;
                            if (node && node.parentNode) node.parentNode.removeChild(node);
                        } catch (e) {}
                    });
                }
            };

            window.WtaScreenCapture = api;
        })();
    """.trimIndent()
}
