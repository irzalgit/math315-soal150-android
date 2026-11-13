/**
 * MATH315 UNIVERSAL AUTHENTICATION & SESSION MODULE (auth.js)
 * Menyediakan berbagai metode login:
 * 1. WhatsApp / No. HP + Nama (Praktis untuk siswa)
 * 2. Email & Password (Daftar / Masuk standar)
 * 3. Kode Voucher / Aktivasi Lisensi Premium
 * 4. Google Sign-In (Native Capacitor & Web Popup)
 */

(function (window) {
  'use strict';

  var firebaseConfig = {
    apiKey: "AIzaSyCu4GQa-EOaPhpQP_y_QcsJxs05adQU2-M",
    authDomain: "math314-app.firebaseapp.com",
    databaseURL: "https://math314-app-default-rtdb.firebaseio.com",
    projectId: "math314-app",
    storageBucket: "math314-app.firebasestorage.app",
    messagingSenderId: "9019551034",
    appId: "1:9019551034:web:de0c5e2b8b20c18ca0b403"
  };

  // Inisialisasi Firebase jika library tersedia
  if (typeof firebase !== 'undefined' && !firebase.apps.length) {
    try {
      firebase.initializeApp(firebaseConfig);
    } catch (e) {
      console.warn('Firebase initialize warn:', e);
    }
  }

  var auth = typeof firebase !== 'undefined' ? firebase.auth() : null;
  var db = typeof firebase !== 'undefined' ? firebase.database() : null;

  // Daftar voucher bawaan (bisa diaktifkan offline maupun online)
  // Durasi dalam hari
  var DEFAULT_VOUCHERS = {
    'MATH315JUARA': 30,
    'PREMIUM1BULAN': 30,
    'MATH315VIP': 90,
    'BELAJARSERU': 30,
    'TKA2027JUARA': 60,
    'PROMO10K': 30,
    'GURUMATH315': 365
  };

  var listeners = [];
  var state = {
    user: null,
    isPremium: false,
    premiumUntil: null,
    authLoaded: false,
    profile: null
  };

  // Baca cache dari localStorage untuk loading instan
  try {
    var cachedPrem = localStorage.getItem('math315_is_premium');
    var cachedUntil = localStorage.getItem('math315_premium_until');
    var cachedUser = localStorage.getItem('math315_cached_user');
    if (cachedPrem === 'true') {
      var untilNum = Number(cachedUntil) || 0;
      if (!untilNum || untilNum > Date.now()) {
        state.isPremium = true;
        state.premiumUntil = untilNum || null;
      }
    }
    if (cachedUser) {
      state.user = JSON.parse(cachedUser);
    }
  } catch (e) {}

  function notify() {
    listeners.forEach(function (fn) {
      try { fn(state); } catch (err) { console.error('Listener err:', err); }
    });
  }

  function checkPremiumStatus(val) {
    if (!val) return false;
    var until = Math.max(Number(val.premium_until) || 0, Number(val.premiumExpired) || 0);
    var now = Date.now();
    if (until > now) return true;
    if (val.isPremium === true || val.is_premium === true || val.tka_premium === true) {
      return !until || until > now;
    }
    return false;
  }

  function saveCache() {
    try {
      localStorage.setItem('math315_is_premium', state.isPremium ? 'true' : 'false');
      if (state.premiumUntil) localStorage.setItem('math315_premium_until', String(state.premiumUntil));
      else localStorage.removeItem('math315_premium_until');
      if (state.user) {
        localStorage.setItem('math315_cached_user', JSON.stringify({
          uid: state.user.uid,
          email: state.user.email,
          displayName: state.user.displayName
        }));
      } else {
        localStorage.removeItem('math315_cached_user');
      }
    } catch (e) {}
  }

  // Listener Firebase Auth State
  if (auth) {
    var dbRef = null;
    auth.onAuthStateChanged(function (user) {
      state.user = user;
      state.authLoaded = true;

      if (dbRef) {
        dbRef.off();
        dbRef = null;
      }

      if (user && db) {
        dbRef = db.ref('users/' + user.uid);
        dbRef.on('value', function (snap) {
          var val = snap.val() || {};
          state.profile = val;
          var until = Math.max(Number(val.premium_until) || 0, Number(val.premiumExpired) || 0);
          state.isPremium = checkPremiumStatus(val);
          state.premiumUntil = until || null;
          saveCache();
          notify();
        }, function (err) {
          console.warn('DB read error:', err);
          saveCache();
          notify();
        });
      } else {
        if (!user) {
          state.profile = null;
          state.isPremium = false;
          state.premiumUntil = null;
        }
        saveCache();
        notify();
      }
    });
  } else {
    state.authLoaded = true;
    notify();
  }

  // Normalisasi nomor telepon ke format internasional e.g. 628123456789
  function normalizePhone(phone) {
    if (!phone) return '';
    var clean = String(phone).replace(/[^0-9]/g, '');
    if (clean.startsWith('0')) clean = '62' + clean.slice(1);
    else if (!clean.startsWith('62')) clean = '62' + clean;
    return clean;
  }

  var MathAuth = {
    firebaseConfig: firebaseConfig,
    auth: auth,
    db: db,

    getState: function () {
      return state;
    },

    getIdToken: async function () {
      if (!auth || !auth.currentUser) {
        throw new Error('Silakan login terlebih dahulu.');
      }
      return await auth.currentUser.getIdToken(true);
    },

    onStateChange: function (fn) {
      listeners.push(fn);
      if (state.authLoaded) {
        try { fn(state); } catch (e) {}
      }
      return function unsubscribe() {
        listeners = listeners.filter(function (l) { return l !== fn; });
      };
    },

    getUserName: function () {
      if (state.user) {
        if (state.profile && state.profile.name) return state.profile.name;
        if (state.user.displayName) return state.user.displayName;
        if (state.user.email) {
          if (state.user.email.startsWith('wa_')) {
            var raw = state.user.email.replace('wa_', '').replace('@math315.app', '');
            return 'Member WA (' + raw + ')';
          }
          return state.user.email.split('@')[0];
        }
      }
      return 'Siswa Math315';
    },

    // 1. LOGIN / DAFTAR DENGAN NOMOR WHATSAPP + PIN
    loginWithWhatsApp: async function (phone, pin, name) {
      if (!auth) throw new Error('Firebase Auth belum siap.');
      var norm = normalizePhone(phone);
      if (norm.length < 10) throw new Error('Nomor WhatsApp tidak valid (minimal 10 angka).');
      if (!pin || pin.length < 6) throw new Error('PIN minimal 6 digit untuk keamanan akun.');

      var virtualEmail = 'wa_' + norm + '@math315.app';
      var password = 'WAPIN_' + pin + '_#M315';

      try {
        // Coba login terlebih dahulu
        var cred = await auth.signInWithEmailAndPassword(virtualEmail, password);
        // Jika nama diisi dan belum ada displayName, update
        if (name && cred.user && (!cred.user.displayName || cred.user.displayName === '')) {
          await cred.user.updateProfile({ displayName: name });
        }
        if (db && cred.user) {
          await db.ref('users/' + cred.user.uid).update({
            phone: norm,
            name: name || (cred.user.displayName || 'Member ' + norm),
            last_login: Date.now()
          });
        }
        return cred.user;
      } catch (err) {
        // Jika user belum terdaftar, buat akun baru secara otomatis
        if (err.code === 'auth/user-not-found' || err.code === 'auth/invalid-credential') {
          var newCred = await auth.createUserWithEmailAndPassword(virtualEmail, password);
          if (newCred.user) {
            await newCred.user.updateProfile({ displayName: name || 'Member ' + norm });
            if (db) {
              await db.ref('users/' + newCred.user.uid).set({
                phone: norm,
                name: name || 'Member ' + norm,
                role: 'student',
                created_at: Date.now(),
                isPremium: false
              });
            }
          }
          return newCred.user;
        } else if (err.code === 'auth/wrong-password') {
          throw new Error('PIN salah. Silakan periksa kembali PIN Anda.');
        } else {
          throw err;
        }
      }
    },

    // 2. LOGIN DENGAN EMAIL & PASSWORD
    loginWithEmail: async function (email, password) {
      if (!auth) throw new Error('Firebase Auth belum siap.');
      if (!email || !password) throw new Error('Email dan password wajib diisi.');
      var res = await auth.signInWithEmailAndPassword(email.trim(), password);
      return res.user;
    },

    // 2B. DAFTAR DENGAN EMAIL & PASSWORD
    registerWithEmail: async function (email, password, displayName, phone) {
      if (!auth) throw new Error('Firebase Auth belum siap.');
      if (!email || !password) throw new Error('Email dan password wajib diisi.');
      if (password.length < 6) throw new Error('Password minimal 6 karakter.');

      var res = await auth.createUserWithEmailAndPassword(email.trim(), password);
      if (res.user) {
        if (displayName) {
          await res.user.updateProfile({ displayName: displayName.trim() });
        }
        if (db) {
          await db.ref('users/' + res.user.uid).set({
            name: displayName ? displayName.trim() : email.split('@')[0],
            email: email.trim(),
            phone: phone ? normalizePhone(phone) : '',
            created_at: Date.now(),
            isPremium: false
          });
        }
      }
      return res.user;
    },

    // 2C. LUPA PASSWORD
    resetPassword: async function (email) {
      if (!auth) throw new Error('Firebase Auth belum siap.');
      if (!email) throw new Error('Masukkan email Anda.');
      await auth.sendPasswordResetEmail(email.trim());
    },

    // 3. AKTIVASI KODE VOUCHER / LISENSI
    activateVoucher: async function (code) {
      if (!code) throw new Error('Masukkan kode voucher.');
      var cleanCode = code.trim().toUpperCase().replace(/[^A-Z0-9]/g, '');

      var days = DEFAULT_VOUCHERS[cleanCode];
      var now = Date.now();

      // Cek apakah ada di RTDB (jika path vouchers bisa dibaca, atau validasi kode lokal)
      if (!days) {
        // Cek pola voucher dinamis format: MATH<hari>D misalnya MATH30D, MATH90D, MATH365D
        var match = cleanCode.match(/^MATH([0-9]{1,3})D$/);
        if (match) {
          days = parseInt(match[1], 10);
        }
      }

      if (!days) {
        throw new Error('Kode voucher tidak valid atau sudah kedaluwarsa.');
      }

      var currentUntil = state.premiumUntil && state.premiumUntil > now ? state.premiumUntil : now;
      var newUntil = currentUntil + (days * 24 * 60 * 60 * 1000);

      // Simpan ke Firebase DB jika user sedang login
      if (auth && auth.currentUser && db) {
        await db.ref('users/' + auth.currentUser.uid).update({
          isPremium: true,
          premium_until: newUntil,
          last_voucher: cleanCode,
          voucher_activated_at: now
        });
      }

      // Update state lokal
      state.isPremium = true;
      state.premiumUntil = newUntil;
      saveCache();
      notify();

      return {
        success: true,
        days: days,
        until: newUntil
      };
    },

    // 4. GOOGLE SIGN-IN (DENGAN SAFE FALLBACK)
    loginWithGoogle: async function () {
      if (!auth) throw new Error('Firebase Auth belum siap.');

      var isNative = (typeof window.Capacitor !== 'undefined' && 
                      typeof window.Capacitor.isNativePlatform === 'function' && 
                      window.Capacitor.isNativePlatform()) ||
                     (typeof window.androidBridge !== 'undefined');

      if (isNative) {
        try {
          var GoogleAuth = (window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.GoogleAuth) || 
                           (window.Capacitor && typeof window.Capacitor.registerPlugin === 'function' && window.Capacitor.registerPlugin('GoogleAuth')) ||
                           (window.Plugins && window.Plugins.GoogleAuth);

          if (!GoogleAuth) {
            throw new Error('Plugin Google Native belum siap di aplikasi ini. Silakan gunakan login via WhatsApp atau Email.');
          }

          try {
            await GoogleAuth.initialize({
              clientId: '9019551034-tt3010rv8bbltqtoe5jmj9uvj1m6chia.apps.googleusercontent.com',
              scopes: ['profile', 'email'],
              grantOfflineAccess: true
            });
          } catch (initErr) {
            console.warn('GoogleAuth init:', initErr);
          }

          var googleUser = await GoogleAuth.signIn();
          var idToken = (googleUser && googleUser.authentication && googleUser.authentication.idToken) || 
                        (googleUser && googleUser.idToken);

          if (!idToken) throw new Error('Token login Google tidak diterima.');

          var credential = firebase.auth.GoogleAuthProvider.credential(idToken);
          var res = await auth.signInWithCredential(credential);
          return res.user;
        } catch (err) {
          var msg = err && err.message ? err.message : String(err);
          if (msg.includes('cancel') || msg.includes('12501') || msg.includes('closed')) {
            return null; // Dibatalkan oleh user
          }
          throw new Error('Google Sign-In gagal di aplikasi: ' + msg + '. Silakan gunakan login WhatsApp atau Email.');
        }
      }

      // Fallback web browser
      var provider = new firebase.auth.GoogleAuthProvider();
      provider.setCustomParameters({ prompt: 'select_account' });
      try {
        var resPopup = await auth.signInWithPopup(provider);
        return resPopup.user;
      } catch (err) {
        if (err.code === 'auth/popup-blocked' || err.code === 'auth/operation-not-supported-in-this-environment') {
          throw new Error('Browser memblokir pop-up Google. Gunakan opsi login WhatsApp atau Email di atas.');
        }
        if (err.code === 'auth/popup-closed-by-user') return null;
        throw err;
      }
    },

    // 5. LOGOUT
    logout: async function () {
      if (auth) {
        await auth.signOut();
      }
      state.user = null;
      state.isPremium = false;
      state.premiumUntil = null;
      state.profile = null;
      saveCache();
      notify();
    }
  };

  window.MathAuth = MathAuth;
})(window);
