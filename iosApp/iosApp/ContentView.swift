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

    var body: some View {
        ComposeView()
                .ignoresSafeArea(.keyboard) // Compose has own keyboard handler
                .onChange(of: scenePhase) { phase in
                    switch phase {
                    case .active:
                        MainViewControllerKt.MetaSecretAppDidEnterForeground()
                    case .background:
                        MainViewControllerKt.MetaSecretAppDidEnterBackground()
                    default:
                        break
                    }
                }
    }
}


