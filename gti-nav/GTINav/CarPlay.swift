import CarPlay
import CoreLocation
import MapboxDirections
import MapboxMaps
import MapboxNavigationCore
import MapboxNavigationUIKit
import UIKit

// MARK: - CarPlay

// CarPlay uses Apple's own templates for the turn card and arrival panel, so only the map itself
// can carry the GTI look: dark style, glowing red route, white Polo, 3D buildings and camera.
@MainActor
final class GTICarPlay: NSObject {
    static let shared = GTICarPlay()

    lazy var manager: CarPlayManager = {
        let manager = CarPlayManager(
            navigationProvider: gtiNavigationProvider,
            styles: [GTIDayStyle(), GTINightStyle()]
        )
        manager.delegate = self
        return manager
    }()

    // The map shown before a drive starts.
    func styleBrowsingMap() {
        guard let navigationMapView = manager.carPlayMapViewController?.navigationMapView else { return }
        applyGTIMapLook(to: navigationMapView)
        if let dataSource = navigationMapView.navigationCamera.viewportDataSource as? CarPlayViewportDataSource {
            applyGTICameraOptions(to: &dataSource.options)
        }
    }

    // Same test route as the phone: from the current (simulated) position to Výstaviště Brno.
    func startTestDrive(completion: @escaping () -> Void) {
        let destination = Waypoint(coordinate: testDestination, name: "Výstaviště Brno")
        manager.previewRoutes(to: destination, completionHandler: completion)
    }
}

extension GTICarPlay: CarPlayManagerDelegate {
    // "Test drive" button at the top of the CarPlay map.
    func carPlayManager(
        _ carPlayManager: CarPlayManager,
        trailingNavigationBarButtonsCompatibleWith traitCollection: UITraitCollection,
        in carPlayTemplate: CPMapTemplate,
        for activity: CarPlayActivity
    ) -> [CPBarButton]? {
        guard activity == .browsing else { return nil }
        let testDriveButton = CPBarButton(title: "Test drive") { [weak self] _ in
            self?.startTestDrive {}
        }
        return [testDriveButton]
    }

    // Apply the GTI look to the turn-by-turn map once CarPlay shows it.
    func carPlayManager(
        _ carPlayManager: CarPlayManager,
        didPresent navigationViewController: CarPlayNavigationViewController
    ) {
        guard let navigationMapView = navigationViewController.navigationMapView else { return }
        applyGTIMapLook(to: navigationMapView)
        if let dataSource = navigationMapView.navigationCamera.viewportDataSource as? CarPlayViewportDataSource {
            applyGTICameraOptions(to: &dataSource.options)
        }
    }

    func carPlayManager(
        _ carPlayManager: CarPlayManager,
        routeLineLayerWithIdentifier identifier: String,
        sourceIdentifier: String,
        for parentViewController: UIViewController
    ) -> LineLayer? {
        gtiRouteLineLayer(identifier: identifier, sourceIdentifier: sourceIdentifier)
    }

    func carPlayManager(
        _ carPlayManager: CarPlayManager,
        routeCasingLineLayerWithIdentifier identifier: String,
        sourceIdentifier: String,
        for parentViewController: UIViewController
    ) -> LineLayer? {
        gtiRouteCasingLayer(identifier: identifier, sourceIdentifier: sourceIdentifier)
    }

    // Background of CarPlay's turn-instruction card: near-black instead of the default blue.
    func carPlayManager(
        _ carPlayManager: CarPlayManager,
        guidanceBackgroundColorFor style: UIUserInterfaceStyle
    ) -> UIColor? {
        UIColor(red: 18/255, green: 18/255, blue: 22/255, alpha: 1)
    }
}

// MARK: - CarPlay scene

// Info.plist points CarPlay at this class. It hands the CarPlay window to the Mapbox CarPlayManager.
final class CarPlaySceneDelegate: NSObject, CPTemplateApplicationSceneDelegate {
    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didConnect interfaceController: CPInterfaceController,
        to window: CPWindow
    ) {
        MainActor.assumeIsolated {
            let carPlay = GTICarPlay.shared
            carPlay.manager.templateApplicationScene(
                templateApplicationScene,
                didConnect: interfaceController,
                to: window
            )
            carPlay.styleBrowsingMap()
        }
    }

    func templateApplicationScene(
        _ templateApplicationScene: CPTemplateApplicationScene,
        didDisconnect interfaceController: CPInterfaceController,
        from window: CPWindow
    ) {
        MainActor.assumeIsolated {
            GTICarPlay.shared.manager.templateApplicationScene(
                templateApplicationScene,
                didDisconnect: interfaceController,
                from: window
            )
        }
    }
}
