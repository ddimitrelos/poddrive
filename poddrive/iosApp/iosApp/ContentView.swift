import SwiftUI

struct ContentView: View {
    var body: some View {
        ZStack {
            Color.podBackground
                .ignoresSafeArea()
            Text("PodDrive")
                .foregroundColor(.white)
                .font(.title)
        }
    }
}

#Preview {
    ContentView()
}
