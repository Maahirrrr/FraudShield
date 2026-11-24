"""
One-click unified runner for FraudShield.
Spawns the Python Model Service (Port 8000), Java Core Server (Port 8080),
and serves the Web Dashboard (Port 5000).
"""

import os
import sys
import time
import subprocess
import webbrowser

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
JAVA_DIR = os.path.join(BASE_DIR, "java-core")
MODEL_DIR = os.path.join(BASE_DIR, "model-service")
VENV_PYTHON = os.path.join(BASE_DIR, ".venv", "bin", "python")

def main():
    print("="*60)
    print("           LAUNCHING FRAUDSHIELD FULL PLATFORM            ")
    print("="*60)

    # 1. Start Python Model Microservice on Port 8000
    print("[1/3] Starting Python Model Microservice (Port 8000)...")
    py_proc = subprocess.Popen(
        [VENV_PYTHON, "-m", "uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"],
        cwd=MODEL_DIR
    )
    time.sleep(2)

    # 2. Start Java Core Engine on Port 8085
    print("[2/3] Starting Java Core Engine (Port 8085)...")
    java_cp = f"{JAVA_DIR}/target/classes:{os.path.expanduser('~/.m2/repository/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar')}"
    java_proc = subprocess.Popen(
        ["java", "-Dserver.port=8085", "-cp", java_cp, "com.fraudshield.Main"],
        cwd=JAVA_DIR
    )
    time.sleep(2)

    # 3. Serve Frontend Web Dashboard on Port 5000
    print("[3/3] Serving Web Dashboard on http://localhost:5000...")
    web_proc = subprocess.Popen(
        [VENV_PYTHON, "-m", "http.server", "5000"],
        cwd=os.path.join(BASE_DIR, "web")
    )

    print("\n" + "="*60)
    print("  FRAUDSHIELD IS FULLY OPERATIONAL!")
    print("  - Web Dashboard & Demo Store : http://localhost:5000")
    print("  - Java Core REST API         : http://localhost:8085")
    print("  - Python Model Microservice  : http://localhost:8000")
    print("="*60)
    print("\nPress Ctrl+C to stop all services.\n")

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\n[!] Shutting down all services...")
        py_proc.terminate()
        java_proc.terminate()
        web_proc.terminate()
        print("[+] All services stopped gracefully.")

if __name__ == "__main__":
    main()
