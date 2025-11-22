#!/usr/bin/env python3
"""
Script to automate Central Portal token generation using browser automation.
This script will:
1. Open the Central Portal login page
2. Login with your credentials
3. Navigate to the token generation page
4. Generate a token
5. Extract and save the token to ~/.gradle/gradle.properties
"""

import sys
import os
import subprocess
import json
from pathlib import Path

def check_dependencies():
    """Check if required dependencies are installed."""
    try:
        import selenium
        from selenium import webdriver
        from selenium.webdriver.common.by import By
        from selenium.webdriver.support.ui import WebDriverWait
        from selenium.webdriver.support import expected_conditions as EC
        return True
    except ImportError:
        print("Error: Selenium is not installed.")
        print("Install it with: pip install selenium")
        print("You also need a browser driver (ChromeDriver, GeckoDriver, etc.)")
        return False

def read_credentials():
    """Read credentials from ~/.gradle/gradle.properties."""
    gradle_props = Path.home() / ".gradle" / "gradle.properties"
    if not gradle_props.exists():
        print(f"Error: {gradle_props} not found")
        return None, None
    
    username = None
    password = None
    
    with open(gradle_props, 'r') as f:
        for line in f:
            line = line.strip()
            if line.startswith('mavenCentralUsername=') or line.startswith('ossrhUsername='):
                username = line.split('=', 1)[1]
            elif line.startswith('mavenCentralPassword=') or line.startswith('ossrhPassword='):
                password = line.split('=', 1)[1]
    
    if not username or not password:
        print("Error: Could not find mavenCentralUsername and mavenCentralPassword in ~/.gradle/gradle.properties")
        return None, None
    
    return username, password

def generate_token_with_selenium(username, password):
    """Generate token using Selenium browser automation."""
    from selenium import webdriver
    from selenium.webdriver.common.by import By
    from selenium.webdriver.support.ui import WebDriverWait
    from selenium.webdriver.support import expected_conditions as EC
    from selenium.webdriver.chrome.options import Options
    
    print("Starting browser automation...")
    print("NOTE: This will open a browser window. Please do not close it.")
    print("")
    
    # Setup Chrome options
    chrome_options = Options()
    # Uncomment the next line if you want headless mode (no browser window)
    # chrome_options.add_argument('--headless')
    
    try:
        driver = webdriver.Chrome(options=chrome_options)
    except Exception as e:
        print(f"Error starting Chrome: {e}")
        print("Make sure ChromeDriver is installed and in your PATH")
        return None, None
    
    try:
        # Navigate to Central Portal
        print("Navigating to Central Portal...")
        driver.get("https://central.sonatype.com/")
        
        # Wait for and click sign in
        print("Looking for sign in button...")
        sign_in_button = WebDriverWait(driver, 10).until(
            EC.element_to_be_clickable((By.LINK_TEXT, "Sign In"))
        )
        sign_in_button.click()
        
        # Wait for login form
        print("Waiting for login form...")
        username_field = WebDriverWait(driver, 10).until(
            EC.presence_of_element_located((By.ID, "username"))
        )
        password_field = driver.find_element(By.ID, "password")
        
        # Enter credentials
        print("Entering credentials...")
        username_field.send_keys(username)
        password_field.send_keys(password)
        
        # Submit form
        login_button = driver.find_element(By.CSS_SELECTOR, "button[type='submit']")
        login_button.click()
        
        # Wait for navigation after login
        print("Waiting for login to complete...")
        WebDriverWait(driver, 30).until(
            lambda d: "central.sonatype.com" in d.current_url
        )
        
        # Navigate to token page
        print("Navigating to token generation page...")
        driver.get("https://central.sonatype.com/usertoken")
        
        # Wait for token generation button
        print("Looking for token generation button...")
        generate_button = WebDriverWait(driver, 10).until(
            EC.element_to_be_clickable((By.XPATH, "//button[contains(text(), 'Generate')]"))
        )
        generate_button.click()
        
        # Wait for token modal/form
        print("Waiting for token form...")
        token_name_field = WebDriverWait(driver, 10).until(
            EC.presence_of_element_located((By.NAME, "displayName"))
        )
        
        # Enter token name
        token_name_field.send_keys("vortex-publishing")
        
        # Submit token generation
        submit_button = driver.find_element(By.XPATH, "//button[contains(text(), 'Generate')]")
        submit_button.click()
        
        # Wait for token to be displayed
        print("Waiting for token to be generated...")
        WebDriverWait(driver, 30).until(
            EC.presence_of_element_located((By.CSS_SELECTOR, "input[readonly]"))
        )
        
        # Extract token username and password
        token_inputs = driver.find_elements(By.CSS_SELECTOR, "input[readonly]")
        if len(token_inputs) >= 2:
            token_username = token_inputs[0].get_attribute("value")
            token_password = token_inputs[1].get_attribute("value")
            
            print("\n" + "="*50)
            print("Token generated successfully!")
            print("="*50)
            print(f"Token Username: {token_username}")
            print(f"Token Password: {token_password[:20]}...")
            print("\nIMPORTANT: Save these values now!")
            print("The browser window will close in 30 seconds...")
            
            import time
            time.sleep(30)
            
            return token_username, token_password
        else:
            print("Error: Could not find token values")
            return None, None
            
    except Exception as e:
        print(f"Error during automation: {e}")
        print("You may need to complete the process manually.")
        input("Press Enter to close the browser...")
        return None, None
    finally:
        driver.quit()

def update_gradle_properties(token_username, token_password):
    """Update ~/.gradle/gradle.properties with the token."""
    gradle_props = Path.home() / ".gradle" / "gradle.properties"
    
    # Read existing file
    lines = []
    if gradle_props.exists():
        with open(gradle_props, 'r') as f:
            lines = f.readlines()
    
    # Update or add token properties
    updated = False
    new_lines = []
    for line in lines:
        if line.startswith('mavenCentralToken=') or line.startswith('sonatypeToken='):
            new_lines.append(f'mavenCentralToken={token_password}\n')
            updated = True
        elif line.startswith('mavenCentralUsername=') and 'token' not in line.lower():
            # Keep the original username, but we'll use token username
            pass
        else:
            new_lines.append(line)
    
    if not updated:
        # Add token if it wasn't found
        new_lines.append(f'\n# Central Portal Token (generated automatically)\n')
        new_lines.append(f'mavenCentralToken={token_password}\n')
    
    # Write back
    with open(gradle_props, 'w') as f:
        f.writelines(new_lines)
    
    print(f"\nUpdated {gradle_props} with token")
    print("You can now run: ./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository")

def main():
    print("="*50)
    print("Central Portal Token Generator")
    print("="*50)
    print("")
    
    if not check_dependencies():
        sys.exit(1)
    
    username, password = read_credentials()
    if not username or not password:
        sys.exit(1)
    
    print(f"Found credentials for user: {username}")
    print("")
    print("This script will:")
    print("1. Open a browser window")
    print("2. Login to Central Portal")
    print("3. Generate a token")
    print("4. Save the token to ~/.gradle/gradle.properties")
    print("")
    response = input("Continue? (y/n): ")
    if response.lower() != 'y':
        print("Cancelled")
        sys.exit(0)
    
    token_username, token_password = generate_token_with_selenium(username, password)
    
    if token_username and token_password:
        update_gradle_properties(token_username, token_password)
        print("\nToken generation complete!")
    else:
        print("\nToken generation failed. Please generate manually:")
        print("1. Go to https://central.sonatype.com/usertoken")
        print("2. Generate a token")
        print("3. Add mavenCentralToken=<token-password> to ~/.gradle/gradle.properties")

if __name__ == "__main__":
    main()

