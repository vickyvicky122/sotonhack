#!/usr/bin/env bash
# ============================================================
#  squish.space -- Setup and Run
#  Works on macOS and Debian/Ubuntu Linux.
#  Checks for a compatible JDK (11-21) and Node.js, installs
#  them automatically if missing, then starts the dev server.
# ============================================================

set -euo pipefail

BOLD="\033[1m"
GREEN="\033[0;32m"
YELLOW="\033[0;33m"
RED="\033[0;31m"
RESET="\033[0m"

info()    { echo -e "${YELLOW}[INFO]${RESET} $*"; }
ok()      { echo -e "${GREEN}[OK]${RESET}   $*"; }
err()     { echo -e "${RED}[ERROR]${RESET} $*" >&2; }
die()     { err "$*"; exit 1; }

echo -e "${BOLD}============================================${RESET}"
echo -e "${BOLD}  squish.space -- Setup and Run${RESET}"
echo -e "${BOLD}============================================${RESET}"
echo ""

OS="$(uname -s)"

# ============================================================
#  Helpers
# ============================================================

# Returns the numeric major version of a java binary, or 0 on failure.
java_major_version() {
    local java_bin="$1"
    local raw
    raw=$("$java_bin" -version 2>&1 | awk -F '"' '/version/ { print $2 }')
    # Handles both "1.8.x" (old style) and "17.x.x" (new style)
    local major
    major=$(echo "$raw" | cut -d'.' -f1)
    if [ "$major" = "1" ]; then
        major=$(echo "$raw" | cut -d'.' -f2)
    fi
    echo "${major:-0}"
}

# Sets GRADLE_JAVA to the home directory of a JDK whose major version
# is between MIN_JAVA and MAX_JAVA (inclusive).  Returns 1 if not found.
MIN_JAVA=11
MAX_JAVA=21
GRADLE_JAVA=""

check_java_candidate() {
    local home="$1"
    local bin="$home/bin/java"
    [ -x "$bin" ] || return 1
    local major
    major=$(java_major_version "$bin")
    if [ "$major" -ge "$MIN_JAVA" ] && [ "$major" -le "$MAX_JAVA" ]; then
        GRADLE_JAVA="$home"
        return 0
    fi
    return 1
}

# ============================================================
#  STEP 1 -- Find a compatible JDK (11-21)
#  Gradle 8.5 supports Java 8-21. Java 22+ will cause a build
#  failure, so we must not blindly use the system default.
# ============================================================

find_compatible_java() {
    # 1. Respect JAVA_HOME if it is already set and compatible.
    if [ -n "${JAVA_HOME:-}" ]; then
        check_java_candidate "$JAVA_HOME" && return 0
    fi

    # 2. macOS -- /usr/libexec/java_home can enumerate installs.
    if [ "$OS" = "Darwin" ] && [ -x /usr/libexec/java_home ]; then
        for ver in 21 17 11; do
            local candidate
            candidate=$(/usr/libexec/java_home -v "$ver" 2>/dev/null || true)
            if [ -n "$candidate" ]; then
                check_java_candidate "$candidate" && return 0
            fi
        done
    fi

    # 3. Eclipse Temurin / Adoptium + OpenJDK (cross-platform install).
    for candidate in \
        "/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home" \
        "/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home" \
        "/Library/Java/JavaVirtualMachines/temurin-11.jdk/Contents/Home" \
        "/usr/lib/jvm/temurin-21-amd64"  \
        "/usr/lib/jvm/temurin-17-amd64"  \
        "/usr/lib/jvm/temurin-11-amd64"  \
        "/usr/lib/jvm/java-21-temurin-jdk" \
        "/usr/lib/jvm/java-17-temurin-jdk" \
        "/usr/lib/jvm/java-11-temurin-jdk" \
        "/usr/lib/jvm/java-21-openjdk-amd64" \
        "/usr/lib/jvm/java-17-openjdk-amd64" \
        "/usr/lib/jvm/java-11-openjdk-amd64" \
        "/usr/lib/jvm/java-21-openjdk" \
        "/usr/lib/jvm/java-17-openjdk" \
        "/usr/lib/jvm/java-11-openjdk" \
    ; do
        check_java_candidate "$candidate" && return 0
    done

    # 4. SDKMAN candidates.
    for ver in 21 17 11; do
        local candidate="$HOME/.sdkman/candidates/java/$ver/home"
        check_java_candidate "$candidate" && return 0
    done

    # 5. Homebrew-managed JDKs (macOS / Linux).
    if command -v brew &>/dev/null; then
        local prefix
        prefix="$(brew --prefix 2>/dev/null || true)"
        for ver in 21 17; do
            check_java_candidate "$prefix/opt/openjdk@$ver" && return 0
        done
    fi

    return 1
}

if ! find_compatible_java; then
    info "No compatible JDK (${MIN_JAVA}-${MAX_JAVA}) found. Attempting to install JDK 17..."
    echo ""

    case "$OS" in
        Darwin)
            if command -v brew &>/dev/null; then
                info "Installing Eclipse Temurin 17 via Homebrew..."
                brew install --cask temurin@17
            else
                die "Homebrew is not installed.\n  Please install it from https://brew.sh/ then re-run this script,\n  or install JDK 17 manually from https://adoptium.net/"
            fi
            ;;
        Linux)
            if command -v apt-get &>/dev/null; then
                info "Adding Adoptium package repository and installing Temurin 17..."
                # Add the Adoptium GPG key and repo, then install.
                sudo apt-get install -y wget apt-transport-https gnupg 2>/dev/null || true
                wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public \
                    | sudo gpg --dearmor -o /usr/share/keyrings/adoptium.gpg
                echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] \
https://packages.adoptium.net/artifactory/deb \
$(. /etc/os-release && echo "$VERSION_CODENAME") main" \
                    | sudo tee /etc/apt/sources.list.d/adoptium.list > /dev/null
                sudo apt-get update -q
                sudo apt-get install -y temurin-17-jdk
            elif command -v dnf &>/dev/null; then
                info "Installing java-17-openjdk-devel via dnf..."
                sudo dnf install -y java-17-openjdk-devel
            elif command -v yum &>/dev/null; then
                info "Installing java-17-openjdk-devel via yum..."
                sudo yum install -y java-17-openjdk-devel
            else
                die "Could not find a supported package manager (apt / dnf / yum).\n  Please install JDK 17 manually from https://adoptium.net/ then re-run this script."
            fi
            ;;
        *)
            die "Unsupported OS: $OS\n  Please install JDK 17 from https://adoptium.net/ then re-run this script."
            ;;
    esac

    # Re-scan after installation.
    if ! find_compatible_java; then
        die "JDK was installed but could not be located automatically.\n  Set JAVA_HOME to your JDK 17 directory and re-run this script."
    fi
fi

ok "Java  : $GRADLE_JAVA ($(java_major_version "$GRADLE_JAVA/bin/java").x)"

# ============================================================
#  STEP 2 -- Check for Node.js
#  Gradle's Kotlin/JS plugin delegates JS bundling to webpack
#  via Node, so Node must be on PATH before the build starts.
# ============================================================

if ! command -v node &>/dev/null; then
    info "Node.js not found. Attempting to install..."
    echo ""

    case "$OS" in
        Darwin)
            if command -v brew &>/dev/null; then
                info "Installing Node.js via Homebrew..."
                brew install node
            else
                die "Homebrew is not installed.\n  Please install Node.js from https://nodejs.org/ then re-run this script."
            fi
            ;;
        Linux)
            if command -v apt-get &>/dev/null; then
                info "Installing nodejs via apt..."
                sudo apt-get update -q
                sudo apt-get install -y nodejs npm
            elif command -v dnf &>/dev/null; then
                info "Installing nodejs via dnf..."
                sudo dnf install -y nodejs
            elif command -v yum &>/dev/null; then
                info "Installing nodejs via yum..."
                sudo yum install -y nodejs
            else
                die "Could not find a supported package manager.\n  Please install Node.js from https://nodejs.org/ then re-run this script."
            fi
            ;;
        *)
            die "Unsupported OS: $OS\n  Please install Node.js from https://nodejs.org/ then re-run this script."
            ;;
    esac

    command -v node &>/dev/null \
        || die "Node.js was installed but 'node' is still not on PATH.\n  Please open a new terminal and re-run this script."
fi

ok "Node  : $(node --version)"

# ============================================================
#  STEP 3 -- Launch the dev server
# ============================================================

echo ""
echo -e "${BOLD}============================================${RESET}"
echo -e "${BOLD}  Starting at http://localhost:8080/${RESET}"
echo -e "  Press ${BOLD}Ctrl+C${RESET} to stop the server."
echo -e "${BOLD}============================================${RESET}"
echo ""

# Make sure the wrapper is executable (Git may not preserve +x on Windows).
chmod +x gradlew

./gradlew jsBrowserDevelopmentRun "-Dorg.gradle.java.home=$GRADLE_JAVA"
