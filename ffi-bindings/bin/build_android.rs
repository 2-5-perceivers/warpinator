use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::{env, fs};

const LIB_NAME: &str = "libwarpinator";
const CRATE_NAME: &str = "warpinator-ffi";
const BINDGEN_BIN: &str = "bindgen";

const TARGETS: &[&str] = &["armeabi-v7a", "arm64-v8a", "x86", "x86_64"];
const FEATURES: &[&str] = &[
    "virtual_filesystem",
    "power_manager",
    "tracing_android",
    "tracing_release_max_level_debug",
];

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let script_dir = env::var("CARGO_MANIFEST_DIR")
        .map(PathBuf::from)
        .unwrap_or_else(|_| env::current_dir().expect("Failed to get current directory"));

    let ndk_home = resolve_ndk_home(&script_dir);
    if let Some(ref ndk) = ndk_home {
        println!("Using NDK: {}", ndk.display());
    }

    let jni_libs_dir = env::var("JNI_LIBS_DIR")
        .map(PathBuf::from)
        .unwrap_or_else(|_| {
            let app_jni = script_dir.join("../android/app/src/main/jniLibs");
            if app_jni.exists() {
                app_jni
            } else {
                script_dir.join("out").join("android").join("jniLibs")
            }
        });

    let bindings_dir = script_dir.join("bindgen");
    let unstripped_dir = script_dir.join("target").join("unstripped");
    let unstripped_jni_libs = unstripped_dir.join("jniLibs");

    let profile = env::var("PROFILE").unwrap_or_else(|_| "release".to_string());

    let mut cargo_flags: Vec<String> = vec![format!("--features={}", FEATURES.join(","))];
    if profile == "release" {
        cargo_flags.push("--release".to_string());
    }

    check_command("cargo", "cargo not found. Install Rust");
    check_cargo_ndk();

    println!("\nBuilding unstripped version for binding generation (profile: {profile})...");
    fs::create_dir_all(&unstripped_jni_libs)?;

    let strip_config = ["--config", "profile.release.strip=false"];
    let mut cmd = Command::new("cargo");
    cmd.current_dir(&script_dir)
        .arg("ndk")
        .arg("-t")
        .arg(TARGETS[0])
        .args(["-o", unstripped_jni_libs.to_str().unwrap()])
        .arg("build")
        .args(&cargo_flags)
        .args(["--target-dir", unstripped_dir.to_str().unwrap()])
        .args(strip_config)
        .stdout(Stdio::inherit())
        .stderr(Stdio::inherit());

    if let Some(ref ndk) = ndk_home {
        cmd.env("ANDROID_NDK_HOME", ndk);
    }

    let status = cmd.status()?;
    if !status.success() {
        return Err("Unstripped build failed".to_string().into());
    }

    println!("\nGenerating Kotlin bindings...");
    fs::create_dir_all(&bindings_dir)?;

    let reference_lib = unstripped_jni_libs
        .join(TARGETS[0])
        .join(format!("{LIB_NAME}.so"));
    let uniffi_config = script_dir.join("uniffi-android.toml");

    let mut bindgen_cmd = Command::new("cargo");
    bindgen_cmd
        .current_dir(&script_dir)
        .arg("run")
        .args(&cargo_flags)
        .arg("--bin")
        .arg(BINDGEN_BIN)
        .arg("generate")
        .arg("--no-format")
        .arg("--config")
        .arg(&uniffi_config)
        .arg("--library")
        .arg(&reference_lib)
        .arg("--language")
        .arg("kotlin")
        .arg("--out-dir")
        .arg(&bindings_dir)
        .stdout(Stdio::inherit())
        .stderr(Stdio::inherit());

    if let Some(ref ndk) = ndk_home {
        bindgen_cmd.env("ANDROID_NDK_HOME", ndk);
    }

    println!(
        "({})",
        bindgen_cmd
            .get_args()
            .map(|s| s.to_str().unwrap().to_string())
            .collect::<Vec<_>>()
            .join(" ")
    );

    let status = bindgen_cmd.status()?;
    if !status.success() {
        return Err("Binding generation failed".into());
    }

    println!("  ✔ Bindings written to bindgen/");

    let generated_kt = bindings_dir
        .join("org")
        .join("perceivers25")
        .join("warpinator")
        .join("warpinator.kt");
    let app_kt =
        script_dir.join("../android/app/src/main/java/org/perceivers25/warpinator/warpinator.kt");
    if generated_kt.exists() && app_kt.parent().map(|p| p.exists()).unwrap_or(false) {
        fs::copy(&generated_kt, &app_kt)?;
        println!("  ✔ Copied Kotlin bindings to {}", app_kt.display());
    }

    println!(
        "Building {CRATE_NAME} for Android targets (profile: {profile}) into {}...",
        jni_libs_dir.display()
    );
    fs::create_dir_all(&jni_libs_dir)?;

    let platform_args = TARGETS
        .iter()
        .flat_map(|target| ["-t", target])
        .collect::<Vec<_>>();

    let mut cmd = Command::new("cargo");
    cmd.current_dir(&script_dir)
        .arg("ndk")
        .args(&platform_args)
        .args(["-o", jni_libs_dir.to_str().unwrap()])
        .arg("build")
        .args(&cargo_flags)
        .stdout(Stdio::inherit())
        .stderr(Stdio::inherit());

    if let Some(ref ndk) = ndk_home {
        cmd.env("ANDROID_NDK_HOME", ndk);
    }

    let status = cmd.status()?;
    if !status.success() {
        return Err("Build failed".to_string().into());
    }

    println!("\nDone!");
    Ok(())
}

/// Automatically locate Android NDK if ANDROID_NDK_HOME is not explicitly set
fn resolve_ndk_home(script_dir: &Path) -> Option<PathBuf> {
    if let Ok(ndk) = env::var("ANDROID_NDK_HOME") {
        return Some(PathBuf::from(ndk));
    }
    if let Ok(ndk) = env::var("NDK_HOME") {
        return Some(PathBuf::from(ndk));
    }
    let local_props = script_dir.join("../android/local.properties");
    if let Ok(content) = fs::read_to_string(&local_props) {
        let mut sdk_dir = None;
        let mut ndk_dir = None;
        for line in content.lines() {
            let line = line.trim();
            if let Some(val) = line.strip_prefix("ndk.dir=") {
                ndk_dir = Some(val.trim().to_string());
            } else if let Some(val) = line.strip_prefix("sdk.dir=") {
                sdk_dir = Some(val.trim().to_string());
            }
        }
        if let Some(ndk) = ndk_dir {
            let p = PathBuf::from(ndk);
            if p.exists() {
                return Some(p);
            }
        }
        if let Some(sdk) = sdk_dir {
            let ndk_parent = PathBuf::from(sdk).join("ndk");
            if let Ok(entries) = fs::read_dir(&ndk_parent) {
                let mut ndk_dirs: Vec<PathBuf> = entries
                    .filter_map(|e| e.ok().map(|e| e.path()))
                    .filter(|p| p.is_dir())
                    .collect();
                ndk_dirs.sort();
                if let Some(highest_ndk) = ndk_dirs.last() {
                    return Some(highest_ndk.clone());
                }
            }
        }
    }
    None
}

/// Simple check to ensure a command exists in the user's PATH
fn check_command(cmd: &str, error_msg: &str) {
    if Command::new(cmd).arg("--version").output().is_err() {
        eprintln!("❌ {}", error_msg);
        std::process::exit(1);
    }
}

/// Specifically check for cargo-ndk
fn check_cargo_ndk() {
    let output = Command::new("cargo").arg("ndk").arg("--version").output();
    if output.is_err() || !output.unwrap().status.success() {
        eprintln!("❌ cargo-ndk not found. Install it with: cargo install cargo-ndk");
        std::process::exit(1);
    }
}
