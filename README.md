# Codex Terminal (Android)

Ek Android app jisme shell hai aur usme Codex binary chalti hai. GitHub par upload karne ke baad GitHub Actions APK banata hai aur ek Release banata hai, jahan se phone par seedha install ho jaati hai.

## Setup (ek baar)

### Option A: Computer se (sabse aasaan)

Git aur GitHub CLI (`gh`) install karo, phir:

```
./scripts/publish-to-github.sh codex-terminal
```

Script browser mein GitHub login maangegi, private repo banayegi, aur code push karegi. Push hote hi build shuru ho jaata hai.

### Option B: Bina terminal ke (GitHub website)

1. github.com par naya repository banao, name `codex-terminal`, visibility **Private**.
2. Repo khol ke **Add file → Upload files** se is folder ki saari files upload karo.
   `.github/workflows/build-apk.yml` wali file bhi upload honi chahiye. Agar nahi ho rahi, to Option A use karo.
3. Commit karo.

## Build aur install

1. Repo ke **Actions** tab mein "Build APK" run dikhega. Lagbhag 15 se 30 minute lagte hain, kyunki Codex binary lagbhag 300 MB ki hai.
2. Complete hone par repo ke **Releases** section mein `build-N` release hoga, jisme `codex-terminal.apk` attached hogi.
3. Phone ke browser mein ye link kholo (apna username aur repo naam daalo):

   `https://github.com/USERNAME/codex-terminal/releases/latest/download/codex-terminal.apk`

4. APK download karke install karo. Pehli baar Android "unknown apps" allow karne ko kahega, to allow karo.

Naya build hone par same link naya APK dega. Har APK ek hi sideload key se signed hai, to purane version par update ho jaayega.

## Use

- `codex-raw --version`: check karo ki binary chal rahi hai
- `codex-raw doctor`: network aur certificate diagnostics
- `key sk-...`: OpenAI API key save karo (screen par nahi dikhti, agli baar bhi chalti hai)
- `codex "is folder mein kya hai"`: non-interactive `codex exec` chalta hai
- `ls`, `cd`, `pwd`: normal shell commands. `cd` ki state rehti hai.
- `clear`: screen saaf karo

## Kaise kaam karta hai

- Binary `@openai/codex@<version>-linux-arm64` npm package se build ke time par download hoti hai, aur `libcodex.so` naam se app mein daali jaati hai. Android is file ko app ki native library folder mein executable rakhta hai.
- Codex binary ko Android par DNS nahi milta (koi `/etc/resolv.conf` nahi hota). Isliye Codex ke HTTPS traffic ko app ka hi ek chhota local proxy (`ConnectProxy.kt`) handle karta hai. Proxy hostnames Android ke resolver se hal karta hai, aur bytes bina decrypt kiye aage bhejta hai. TLS certificates Codex khud verify karta hai, CA list `cacert.pem` se aati hai.
- Shell ek pipe-based `sh` process hai, pseudo-terminal nahi.

## Limitations

- Full-screen Codex TUI (sirf `codex` likhne par) yahan nahi chalega. `codex "prompt"` use karo.
- Login sirf API key se hai. ChatGPT browser login is app mein nahi hai.
- Command sandbox bypass hai (`--dangerously-bypass-approvals-and-sandbox`), kyunki Android par bubblewrap sandbox nahi chalta. Codex wahi files access kar sakta hai jo ye app access kar sakti hai. Android app sandbox hi boundary hai. Is app mein sensitive files mat rakho.
- API key app ki private storage mein plain text mein save hoti hai. Isliye shared ya sensitive phone par mat use karo.
- Sirf arm64 phones (Android 8.0 / API 26 ya naya).
- Ye code compile aur phone par test nahi hua hai. Pehla build fail ho sakta hai. Us run ka error log bhejoge to main fix kar dunga.

## Troubleshooting

- Build fail hua: Actions run kholo, jo step red hai uska log copy karke bhejo.
- `codex "hi"` network error de: `codex-raw doctor` chalao aur output bhejo.
- `Warning: Codex binary not found`: Actions log mein "Fetch Codex arm64 binary" step dekho.

## Files

- `app/src/main/java/com/example/codexterminal/MainActivity.kt`: UI (output box + input box)
- `ShellSession.kt`: long-running shell process aur uska output
- `TerminalEnvironment.kt`: environment variables, `codex` command ko binary call mein badalna, API key handling
- `ConnectProxy.kt`: local HTTP CONNECT proxy (DNS workaround)
- `.github/workflows/build-apk.yml`: binary download, CA bundle download, Gradle build, Release publish
- `app/sideload.keystore`: sideload signing key (sirf sideload ke liye, Play Store ke liye nahi)
- `scripts/publish-to-github.sh`: ek command mein repo banakar push

Package name badalna ho to `app/build.gradle`, `AndroidManifest.xml` aur `com/example/codexterminal` folder badalna hoga.
