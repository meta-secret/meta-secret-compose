import SwiftUI

@main
struct iOSApp: App {
	var body: some Scene {
		WindowGroup {
			ContentView()
			.ignoresSafeArea(edges: .all)
            .ignoresSafeArea(.keyboard)
		}
	}
}
