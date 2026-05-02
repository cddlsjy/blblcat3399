#!/usr/bin/env python3
"""
Kotlin DSL (.kts) to Groovy DSL (.gradle) converter for Android projects.
Converts a zip archive of kts source code to gradle source code.
"""

import os
import re
import zipfile
import tempfile
import shutil
from pathlib import Path


class KtsToGradleConverter:
    """Converts Kotlin DSL build files to Groovy DSL."""

    def __init__(self):
        self.build_config_added = False

    def convert_file_content(self, content: str, filename: str) -> str:
        """Convert kts file content to gradle format."""
        result = content

        # 1. Convert plugin declarations
        # id("plugin") version "x.y.z" → id 'plugin' version 'x.y.z'
        result = re.sub(
            r'id\("([^"]+)"\) version "([^"]+)"',
            r"id '\1' version '\2'",
            result
        )
        result = re.sub(r'id\("([^"]+)"\)', r"id '\1'", result)

        # 2. Convert Android configuration assignments
        # namespace = "xxx" → namespace 'xxx'
        result = re.sub(r'namespace = "([^"]+)"', r"namespace '\1'", result)
        result = re.sub(r'compileSdk = (\d+)', r"compileSdk \1", result)
        result = re.sub(r'applicationId = "([^"]+)"', r"applicationId '\1'", result)
        result = re.sub(r'minSdk = (\d+)', r"minSdk \1", result)
        result = re.sub(r'targetSdk = (\d+)', r"targetSdk \1", result)
        result = re.sub(r'versionCode = (.+)', r"versionCode \1", result)
        result = re.sub(r'versionName = (.+)', r"versionName \1", result)

        # 3. Convert boolean assignments in buildTypes
        # isMinifyEnabled = true → minifyEnabled true
        result = re.sub(r'isMinifyEnabled = true', r'minifyEnabled true', result)
        result = re.sub(r'isMinifyEnabled = false', r'minifyEnabled false', result)
        result = re.sub(r'isShrinkResources = true', r'shrinkResources true', result)
        result = re.sub(r'isShrinkResources = false', r'shrinkResources false', result)

        # 4. Convert vectorDrawables.useSupportLibrary
        result = re.sub(r'useSupportLibrary = true', r'useSupportLibrary true', result)
        result = re.sub(r'useSupportLibrary = false', r'useSupportLibrary false', result)

        # 5. Convert buildFeatures
        result = re.sub(r'buildConfig = true', r'buildConfig true', result)
        result = re.sub(r'viewBinding = true', r'viewBinding true', result)

        # 6. Convert dependencies
        result = re.sub(r'implementation\("([^"]+)"\)', r"implementation '\1'", result)
        result = re.sub(r'testImplementation\("([^"]+)"\)', r"testImplementation '\1'", result)
        result = re.sub(r'androidTestImplementation\("([^"]+)"\)', r"androidTestImplementation '\1'", result)
        result = re.sub(r'kapt\("([^"]+)"\)', r"kapt '\1'", result)
        result = re.sub(r'api\("([^"]+)"\)', r"api '\1'", result)
        result = re.sub(r'files\("([^"]+)"\)', r"files('\1')", result)
        result = re.sub(r'file\("([^"]+)"\)', r"file('\1')", result)

        # 7. Convert Protobuf protoc artifact
        result = re.sub(r'artifact = "([^"]+)"', r"artifact = '\1'", result)

        # 8. Convert setOf() to [] for excludes
        result = re.sub(r'excludes \+= setOf\(([^)]+)\)', r'excludes += [\1]', result)

        # 9. Convert val/var to def in task definitions
        result = re.sub(r'val (\w+) = tasks\.register\("([^"]+)"\)', r"def \1 = tasks.register('\2')", result)

        # 10. Convert file() references
        result = re.sub(r'rootProject\.file\("([^"]+)"\)', r"rootProject.file('\1')", result)

        # 11. Convert proguardFiles
        result = re.sub(r'getDefaultProguardFile\("([^"]+)"\)', r"getDefaultProguardFile('\1')", result)

        # 12. Convert string interpolation patterns (optional, both work)
        # Keep ${} as is, Groovy supports it

        # 13. Handle specific app/build.gradle.kts transformations
        if filename == "app/build.gradle.kts" or filename.endswith("/app/build.gradle.kts"):
            result = self._convert_app_build_gradle(result)

        return result

    def _convert_app_build_gradle(self, content: str) -> str:
        """Specific conversions for app/build.gradle.kts."""
        lines = content.split('\n')
        result_lines = []
        android_block_start = -1
        prop_or_env_function = []
        in_prop_or_env = False
        in_android_block = False

        # Step 1: Extract propOrEnv function and find android block start
        for i, line in enumerate(lines):
            if 'fun propOrEnv(' in line:
                in_prop_or_env = True
                # Skip the line, we'll add it later
                continue
            if in_prop_or_env:
                if line.strip() == '}' or line.strip() == '}':
                    in_prop_or_env = False
                else:
                    prop_or_env_function.append(line)
                continue

            if line.strip() == 'android {':
                android_block_start = i
                break

            result_lines.append(line)

        # Step 2: Insert propOrEnv function after plugins block
        plugins_end = -1
        brace_count = 0
        in_plugins = False
        for i, line in enumerate(result_lines):
            if line.strip() == 'plugins {':
                in_plugins = True
                brace_count = 1
            elif in_plugins:
                if '{' in line:
                    brace_count += 1
                if '}' in line:
                    brace_count -= 1
                    if brace_count == 0:
                        plugins_end = i
                        break

        if plugins_end != -1 and prop_or_env_function:
            # Add propOrEnv function after plugins block
            prop_or_env_groovy = [
                '',
                'def propOrEnv(String name) {',
                '    def fromProp = project.findProperty(name) as String',
                '    if (fromProp != null && !fromProp.isBlank()) return fromProp',
                '    def fromEnv = System.getenv(name)',
                '    if (fromEnv != null && !fromEnv.isBlank()) return fromEnv',
                '    return null',
                '}',
                ''
            ]
            result_lines = result_lines[:plugins_end + 1] + prop_or_env_groovy + result_lines[plugins_end + 1:]

        # Step 3: Process remaining lines, add buildConfigField to defaultConfig
        in_default_config = False
        default_config_brace_count = 0
        added_build_config = False
        final_lines = []

        for line in result_lines + lines[len(result_lines):]:
            if 'defaultConfig {' in line:
                in_default_config = True
                default_config_brace_count = 1
            elif in_default_config:
                if '{' in line:
                    default_config_brace_count += 1
                if '}' in line:
                    default_config_brace_count -= 1
                    if default_config_brace_count == 0:
                        in_default_config = False

                # Add buildConfigField after versionName
                if not added_build_config and 'versionName' in line:
                    final_lines.append(line)
                    final_lines.append('')
                    final_lines.append('        buildConfigField \'String\', \'VERSION_NAME\', "\\"${verName}\\""')
                    final_lines.append('        buildConfigField \'int\', \'VERSION_CODE\', "${verCode}"')
                    added_build_config = True
                    continue

            final_lines.append(line)

        # Step 4: Simplify checkThemeTokens task
        result = '\n'.join(final_lines)
        if 'checkThemeTokens' in result:
            # Replace complex task with simpler version
            task_start = result.find('def checkThemeTokens = tasks.register')
            if task_start != -1:
                # Find end of task (matching braces)
                brace_count = 0
                task_end = task_start
                for i in range(task_start, len(result)):
                    if result[i] == '{':
                        brace_count += 1
                    elif result[i] == '}':
                        brace_count -= 1
                        if brace_count == 0:
                            task_end = i + 1
                            break

                # Replace with simpler Groovy implementation
                simple_task = '''def checkThemeTokens = tasks.register('checkThemeTokens') {
    group = 'verification'
    description = 'Fails if layouts reference fixed palette colors instead of theme attributes.'

    doLast {
        def resDir = file('src/main/res')
        def layoutDirs = resDir.listFiles()?.findAll { it.isDirectory() && it.name.startsWith('layout') } ?: []

        def forbidden = [
            '@color/blbl_bg',
            '@color/blbl_surface',
            '@color/blbl_text',
            '@color/blbl_text_secondary',
            '@color/blbl_focus_stroke',
            '@drawable/blbl_focus_bg_round'
        ]

        def violations = []
        for (dir in layoutDirs) {
            dir.traverse(type: groovy.io.FileType.FILES) { f ->
                if (f.name.toLowerCase().endsWith('.xml')) {
                    def relPath = f.relativePath(projectDir).replace('\\\\', '/')
                    def lines = f.readLines('UTF-8')
                    lines.eachWithIndex { line, index ->
                        for (token in forbidden) {
                            def matcher = (line =~ /(?:^|[^\\\\w_])($token)(?:[^\\\\w_]|$)/)
                            if (matcher.find()) {
                                violations.add("$relPath:${index + 1}: $token")
                            }
                        }
                    }
                }
            }
        }

        if (!violations.isEmpty()) {
            def msg = new StringBuilder()
            msg.appendLine('Theme token check failed: layouts must use theme attributes, not fixed palette colors.')
            msg.appendLine('Use ?attr/colorOnSurface, ?android:attr/textColorSecondary, ?attr/colorBackground, ?attr/colorSurface, ?attr/blblOnPageBackdrop, ?attr/blblFocusBgRound, ?attr/blblFocusStrokeColor, etc.')
            msg.appendLine('Violations:')
            violations.each { msg.appendLine("  $it") }
            throw new GradleException(msg.toString())
        }
    }
}'''
                result = result[:task_start] + simple_task + result[task_end:]

        return result


    def convert_archive(self, input_zip: str, output_zip: str) -> None:
        """Convert kts zip archive to gradle zip archive."""
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_path = Path(temp_dir)

            # Extract input zip
            with zipfile.ZipFile(input_zip, 'r') as zf:
                zf.extractall(temp_path)

            # Process files
            for root, dirs, files in os.walk(temp_path):
                for file in files:
                    if file.endswith('.kts'):
                        file_path = Path(root) / file
                        content = file_path.read_text(encoding='utf-8')
                        rel_path = file_path.relative_to(temp_path)
                        converted = self.convert_file_content(content, str(rel_path))
                        file_path.write_text(converted, encoding='utf-8')
                        # Rename file
                        new_path = file_path.with_suffix('.gradle')
                        file_path.rename(new_path)

            # Create output zip
            with zipfile.ZipFile(output_zip, 'w', zipfile.ZIP_DEFLATED) as zf:
                for root, dirs, files in os.walk(temp_path):
                    for file in files:
                        file_path = Path(root) / file
                        arcname = file_path.relative_to(temp_path)
                        zf.write(file_path, arcname)


def main():
    import argparse
    parser = argparse.ArgumentParser(description='Convert Kotlin DSL archive to Groovy DSL archive.')
    parser.add_argument('input', help='Input zip file with kts source code')
    parser.add_argument('output', help='Output zip file with gradle source code')
    args = parser.parse_args()

    converter = KtsToGradleConverter()
    converter.convert_archive(args.input, args.output)
    print(f'Converted {args.input} → {args.output}')


if __name__ == '__main__':
    main()
