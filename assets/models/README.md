# Native ALPR ONNX models

Bundled native ALPR models live here:

- `yolo-v9-t-640-license-plates-end2end.onnx` - license plate detector
- `global_mobile_vit_v2_ocr.onnx` - plate OCR
- `reported-plate-class-best.onnx` - plate state classifier
- `reported-v13-optimized.onnx` - Reported complaint detector for scanner pre-categorization

Android packages this directory as assets. iOS keeps its own copy under
`native/iosApp/ReportediOS/Resources/models`.
