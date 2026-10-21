import CarPlay
import Combine
import CoreLocation
import MapboxDirections
import MapboxMaps
import MapboxNavigationCore
// The CarPlay delegate has two internal requirements whose default versions are only visible
// through this SPI import (the SDK's own CarPlay example imports it the same way).
@_spi(MapboxCarPlayInternal) import MapboxNavigationUIKit
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

    // The trip being previewed or driven, needed to update its time estimates.
    private var activeTrip: CPTrip?
    private var etaObserver: AnyCancellable?

    // The map shown before a drive starts.
    func styleBrowsingMap() {
        manager.carPlayMapViewController?.wayNameView?.label.superview?.addGTIGlass(.capsule)
        guard let navigationMapView = manager.carPlayMapViewController?.navigationMapView else { return }
        applyGTIMapLook(to: navigationMapView, carSize: carPlayCarScale)
        if let dataSource = navigationMapView.navigationCamera.viewportDataSource as? CarPlayViewportDataSource {
            applyGTICameraOptions(to: &dataSource.options)
        }
    }

    // Same test route as the phone: from the current (simulated) position to Výstaviště Brno.
    // CarPlay's route preview has a green Go button whose colour apps can't change, so start
    // driving as soon as the route is ready instead (the same thing pressing Go does).
    func startTestDrive() {
        let destination = Waypoint(coordinate: testDestination, name: "Výstaviště Brno")
        manager.previewRoutes(to: destination) { [weak self] in
            MainActor.assumeIsolated { self?.startPreviewedTrip() }
        }
    }

    private func startPreviewedTrip() {
        guard let activeTrip,
              let routeChoice = activeTrip.routeChoices.first,
              let previewTemplate = manager.interfaceController?.topTemplate as? CPMapTemplate else { return }
        manager.mapTemplate(previewTemplate, startedTrip: activeTrip, using: routeChoice)
    }

    // The SDK colours the remaining time by traffic (usually green). CarPlay only offers Apple's
    // green, orange and red, none of which match the GTI red, so after each SDK update send the
    // same estimates again in the default white.
    func startWhiteETA() {
        etaObserver = gtiNavigationProvider.navigation().routeProgress
            .compactMap { $0?.routeProgress }
            .receive(on: DispatchQueue.main) // runs after the SDK's own update
            .sink { [weak self] progress in
                MainActor.assumeIsolated { self?.showWhiteETA(progress) }
            }
    }

    func stopWhiteETA() {
        etaObserver = nil
        activeTrip = nil
    }

    private func showWhiteETA(_ progress: RouteProgress) {
        guard let activeTrip,
              let template = manager.interfaceController?.rootTemplate as? CPMapTemplate else { return }
        let metres = progress.distanceRemaining
        let distance = metres >= 1000
            ? Measurement(value: (metres / 100).rounded() / 10, unit: UnitLength.kilometers)
            : Measurement(value: (metres / 50).rounded() * 50, unit: UnitLength.meters)
        // Like the SDK: "1 min" instead of "0 min" in the last minute.
        let seconds = progress.durationRemaining > 0 ? max(progress.durationRemaining, 60) : 0
        let estimates = CPTravelEstimates(distanceRemaining: distance, timeRemaining: seconds)
        template.update(estimates, for: activeTrip, with: .default)
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
        if activity == .navigating { return [] }
        guard activity == .browsing else { return nil }
        let testDriveButton = CPBarButton(title: "Test drive") { [weak self] _ in
            self?.startTestDrive()
        }
        return [testDriveButton]
    }

    // While driving, everything is controlled from the phone, so CarPlay shows no map buttons
    // (no route overview or feedback) and no bar buttons (no End, Unmute or Alternatives).
    func carPlayManager(
        _ carPlayManager: CarPlayManager,
        mapButtonsCompatibleWith traitCollection: UITraitCollection,
        in carPlayTemplate: CPTemplate,
        for activity: CarPlayActivity
    ) -> [CPMapButton]? {
        activity == .navigating ? [] : nil
    }

    // Remember the trip so its estimates can be shown in white once driving starts.
    func carPlayManager(_ carPlayManager: CarPlayManager, willPreview trip: CPTrip) -> CPTrip {
        activeTrip = trip
        return trip
    }

    func carPlayManagerWillEndNavigation(_ carPlayManager: CarPlayManager, byCanceling canceled: Bool) {
        stopWhiteETA()
    }

    // The browsing map (before a drive) is ready: apply the GTI look.
    func carPlayManager(_ carPlayManager: CarPlayManager, didSetup navigationMapView: NavigationMapView) {
        carPlayManager.carPlayMapViewController?.wayNameView?.label.superview?.addGTIGlass(.capsule)
        applyGTIMapLook(to: navigationMapView, carSize: carPlayCarScale)
        if let dataSource = navigationMapView.navigationCamera.viewportDataSource as? CarPlayViewportDataSource {
            applyGTICameraOptions(to: &dataSource.options)
        }
    }

    // Leading buttons (Unmute and Alternatives while driving): none while driving.
    func carPlayManager(
        _ carPlayManager: CarPlayManager,
        leadingNavigationBarButtonsCompatibleWith traitCollection: UITraitCollection,
        in carPlayTemplate: CPMapTemplate,
        for activity: CarPlayActivity
    ) -> [CPBarButton]? {
        activity == .navigating ? [] : nil
    }

    // The SDK's internal variant of the above, called when the camera changes.
    func carPlayManager(
        _ carPlayManager: CarPlayManager,
        leadingNavigationBarButtonsCompatibleWith traitCollection: UITraitCollection,
        in carPlayTemplate: CPMapTemplate,
        for activity: CarPlayActivity,
        cameraState: NavigationCameraState
    ) -> [CPBarButton]? {
        activity == .navigating ? [] : nil
    }

    // Apply the GTI look to the turn-by-turn map once CarPlay shows it.
    func carPlayManager(
        _ carPlayManager: CarPlayManager,
        didPresent navigationViewController: CarPlayNavigationViewController
    ) {
        navigationViewController.wayNameView?.label.superview?.addGTIGlass(.capsule)
        startWhiteETA()
        guard let navigationMapView = navigationViewController.navigationMapView else { return }
        applyGTIMapLook(to: navigationMapView, carSize: carPlayCarScale)
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
