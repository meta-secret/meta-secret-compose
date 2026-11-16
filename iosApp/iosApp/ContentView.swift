import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    @Environment(\.scenePhase) private var scenePhase

    @State private var wasBackground = false

    var body: some View {
        ZStack {
            ComposeView().ignoresSafeArea(.keyboard).accessibilityHidden(scenePhase != .active)
            if scenePhase != .active {
                Color(red: 44.0 / 255, green: 46.0 / 255, blue: 47.0 / 255)
                    .ignoresSafeArea().accessibilityHidden(true)
            }
        }
                .onChange(of: scenePhase) { phase in
                    switch phase {
                    case .active:
                        // An OS prompt's inactive return keeps its selected auth intent/form.
                        if wasBackground { MainViewControllerKt.MetaSecretAppDidEnterForeground() }
                        wasBackground = false
                    case .background:
                        wasBackground = true
                        MainViewControllerKt.MetaSecretAppDidEnterBackground()
                    default:
                        break
                    }
                }
    }
}
