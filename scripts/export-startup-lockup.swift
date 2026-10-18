// Optional maintenance export. Normal Gradle/Xcode builds consume checked-in assets.
import Foundation
import CoreGraphics
import CoreText

let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
let check = CommandLine.arguments.dropFirst().contains("--check")
let fontURL = root.appendingPathComponent("composeApp/src/commonMain/composeResources/font/orbit_manrope_regular.otf")
guard let provider = CGDataProvider(url: fontURL as CFURL), let graphicsFont = CGFont(provider) else {
    fatalError("Run from meta-secret-compose with the bundled Manrope font present")
}
let canvas = CGSize(width: 160, height: 64)
let blockHeight: CGFloat = 34.8 + 7 + 16.5
let blockTop = (canvas.height - blockHeight) / 2

struct Line {
    let text: String
    let size: CGFloat
    let tracking: CGFloat
    let height: CGFloat
    let top: CGFloat
    let color: String
}

let lines = [
    Line(text: "MetaSecret", size: 29, tracking: -1.15, height: 34.8, top: blockTop, color: "F1F1EE"),
    Line(text: "Your network of Truth", size: 11, tracking: 0.2, height: 16.5, top: blockTop + 34.8 + 7, color: "C0C4C1"),
]

func number(_ value: CGFloat) -> String {
    precondition(value.isFinite, "Nonfinite path coordinate")
    let stable = abs(value) < 0.000005 ? 0 : value
    var result = String(format: "%.5f", locale: Locale(identifier: "en_US_POSIX"), Double(stable))
    while result.last == "0" { result.removeLast() }
    if result.last == "." { result.removeLast() }
    return result
}

func outline(_ spec: Line) -> CGPath {
    let font = CTFontCreateWithGraphicsFont(graphicsFont, spec.size, nil, nil)
    let attributes: [NSAttributedString.Key: Any] = [
        NSAttributedString.Key(kCTFontAttributeName as String): font,
        NSAttributedString.Key(kCTKernAttributeName as String): spec.tracking,
    ]
    let line = CTLineCreateWithAttributedString(NSAttributedString(string: spec.text, attributes: attributes))
    var ascent: CGFloat = 0, descent: CGFloat = 0
    let advance = CGFloat(CTLineGetTypographicBounds(line, &ascent, &descent, nil))
    let baseline = canvas.height - (spec.top + (spec.height - ascent - descent) / 2 + ascent)
    let path = CGMutablePath()
    for run in CTLineGetGlyphRuns(line) as! [CTRun] {
        let count = CTRunGetGlyphCount(run)
        var glyphs = [CGGlyph](repeating: 0, count: count)
        var positions = [CGPoint](repeating: .zero, count: count)
        CTRunGetGlyphs(run, CFRange(location: 0, length: 0), &glyphs)
        CTRunGetPositions(run, CFRange(location: 0, length: 0), &positions)
        for index in 0..<count {
            guard let glyph = CTFontCreatePathForGlyph(font, glyphs[index], nil) else { continue }
            let transform = CGAffineTransform(translationX: (canvas.width - advance) / 2 + positions[index].x,
                                             y: baseline + positions[index].y)
            path.addPath(glyph, transform: transform)
        }
    }
    return path
}

// CoreText paths retain real font contours. Emit only supported vector/PDF operators.
func commands(_ path: CGPath, pdf: Bool) -> String {
    var result = [String]()
    var current = CGPoint.zero
    var start = CGPoint.zero
    func point(_ p: CGPoint) -> String { "\(number(p.x)) \(number(p.y))" }
    path.applyWithBlock { pointer in
        let element = pointer.pointee
        switch element.type {
        case .moveToPoint:
            current = element.points[0]; start = current
            result.append(pdf ? "\(point(current)) m" : "M\(point(current))")
        case .addLineToPoint:
            current = element.points[0]
            result.append(pdf ? "\(point(current)) l" : "L\(point(current))")
        case .addQuadCurveToPoint:
            let control = element.points[0], end = element.points[1]
            let first = CGPoint(x: current.x + (control.x - current.x) * 2 / 3,
                                y: current.y + (control.y - current.y) * 2 / 3)
            let second = CGPoint(x: end.x + (control.x - end.x) * 2 / 3,
                                 y: end.y + (control.y - end.y) * 2 / 3)
            let points = "\(point(first)) \(point(second)) \(point(end))"
            result.append(pdf ? "\(points) c" : "C\(points)"); current = end
        case .addCurveToPoint:
            let points = "\(point(element.points[0])) \(point(element.points[1])) \(point(element.points[2]))"
            result.append(pdf ? "\(points) c" : "C\(points)"); current = element.points[2]
        case .closeSubpath:
            result.append(pdf ? "h" : "Z"); current = start
        @unknown default: fatalError("Unsupported glyph path element")
        }
    }
    return result.joined(separator: pdf ? "\n" : " ")
}

func write(_ relative: String, _ data: Data) throws {
    let target = root.appendingPathComponent(relative)
    if check {
        guard try Data(contentsOf: target) == data else { fatalError("Stale startup asset: \(relative)") }
    } else {
        try FileManager.default.createDirectory(at: target.deletingLastPathComponent(), withIntermediateDirectories: true)
        try data.write(to: target, options: .atomic)
    }
}

var xmlPaths = [String]()
var pdfContent = ""
var maximumRadius: CGFloat = 0
for spec in lines {
    let path = outline(spec)
    // PDF uses y-up; Android uses y-down, centered inside the 288dp system slot.
    var transform = CGAffineTransform(a: 1, b: 0, c: 0, d: -1,
                                     tx: (288 - canvas.width) / 2, ty: (288 + canvas.height) / 2)
    let android = path.copy(using: &transform)!
    let bounds = android.boundingBoxOfPath
    for x in [bounds.minX, bounds.maxX] {
        for y in [bounds.minY, bounds.maxY] {
            maximumRadius = max(maximumRadius, hypot(x - 144, y - 144))
        }
    }
    precondition(maximumRadius < 96, "Text exceeds Android's 192dp safe circle")
    xmlPaths.append("    <path android:fillColor=\"#\(spec.color)\" android:pathData=\"\(commands(android, pdf: false))\" />")
    let rgb = stride(from: 0, to: 6, by: 2).map { offset -> String in
        let begin = spec.color.index(spec.color.startIndex, offsetBy: offset)
        let end = spec.color.index(begin, offsetBy: 2)
        return number(CGFloat(Int(spec.color[begin..<end], radix: 16)!) / 255)
    }.joined(separator: " ")
    pdfContent += "\(rgb) rg\n\(commands(path, pdf: true))\nf\n"
}

let xml = """
<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by scripts/export-startup-lockup.swift from bundled Manrope. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="288dp" android:height="288dp"
    android:viewportWidth="288" android:viewportHeight="288">
\(xmlPaths.joined(separator: "\n"))
</vector>

"""
try write("composeApp/src/androidMain/res/drawable/startup_lockup.xml", Data(xml.utf8))

// Minimal deterministic vector PDF: no embedded font, timestamps or random IDs.
let objects = [
    "<< /Type /Catalog /Pages 2 0 R >>",
    "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 160 64] /Resources << >> /Contents 4 0 R >>",
    "<< /Length \(pdfContent.utf8.count) >>\nstream\n\(pdfContent)endstream",
]
var pdf = "%PDF-1.4\n"
var offsets = [Int]()
for (index, object) in objects.enumerated() {
    offsets.append(pdf.utf8.count)
    pdf += "\(index + 1) 0 obj\n\(object)\nendobj\n"
}
let xref = pdf.utf8.count
pdf += "xref\n0 5\n0000000000 65535 f \n"
for offset in offsets { pdf += String(format: "%010d 00000 n \n", offset) }
pdf += "trailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n\(xref)\n%%EOF\n"
try write("iosApp/iosApp/Assets.xcassets/StartupLockup.imageset/startup-lockup.pdf", Data(pdf.utf8))
print("Startup assets \(check ? "verified" : "exported"); exact glyph-bound maximum radius \(number(maximumRadius))dp <96dp")
