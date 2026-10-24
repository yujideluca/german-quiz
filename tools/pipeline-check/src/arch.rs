//! Check b: the pure module stays free of the forbidden packages (read from the source, not only the build file).
use crate::config::Architecture;
use crate::fsutil::{has_ext, walk};
use crate::report::{Check, Status};
use std::path::Path;

pub const ID: &str = "arquitetura";

fn import_violates(line: &str, forbidden: &[String]) -> bool {
    let Some(rest) = line.trim_start().strip_prefix("import ") else { return false };
    let path = rest.trim().trim_end_matches(';');
    forbidden.iter().any(|f| path == f || path.starts_with(&format!("{f}.")))
}

pub fn check(root: &Path, cfg: &Architecture) -> Check {
    let exts = vec!["kt".to_string(), "java".to_string()];
    let mut violations: Vec<String> = Vec::new();
    for dir in &cfg.pure_dirs {
        for file in walk(&root.join(dir)).into_iter().filter(|p| has_ext(p, &exts)) {
            let text = std::fs::read_to_string(&file).unwrap_or_default();
            for (i, line) in text.lines().enumerate() {
                if import_violates(line, &cfg.forbidden) {
                    let rel = file.strip_prefix(root).unwrap_or(&file).display();
                    violations.push(format!("{rel}:{}: {}", i + 1, line.trim()));
                }
            }
        }
    }
    for build in &cfg.build_files {
        let text = std::fs::read_to_string(root.join(build)).unwrap_or_default();
        for (i, line) in text.lines().enumerate() {
            let code = line.trim_start();
            let lower = code.to_lowercase();
            if !code.starts_with("//") && cfg.forbidden.iter().any(|f| lower.contains(&f.to_lowercase())) {
                violations.push(format!("{build}:{}: {}", i + 1, code));
            }
        }
    }
    let status = if violations.is_empty() { Status::Ok } else { Status::Falha };
    let nota = (!violations.is_empty()).then(|| violations.join("; "));
    Check::new(ID, status, Some(violations.len() as f64), Some(0.0), nota)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn forbidden() -> Vec<String> {
        vec!["android".into(), "androidx".into()]
    }

    // REQ PC-3
    #[test]
    fn android_and_androidx_imports_are_violations_but_lookalikes_are_not() {
        let f = forbidden();
        assert!(import_violates("import android.os.Bundle", &f));
        assert!(import_violates("  import androidx.compose.runtime.Composable", &f));
        assert!(import_violates("import android.*;", &f));
        assert!(!import_violates("import dev.yuji.androidish.Thing", &f));
        assert!(!import_violates("import kotlin.math.max", &f));
        assert!(!import_violates("// import android.os.Bundle", &f));
    }
}
