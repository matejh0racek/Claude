import UIKit
import CoreLocation
import MapboxMaps
import MapboxDirections
import MapboxNavigationCore
import MapboxNavigationUIKit

// MARK: - Your settings

// Paste your style URL from Mapbox Studio (Share → Style URL).
// While it still contains "YOUR_", the app uses the bundled gti-dark-style.json instead.
let studioStyleURL = "mapbox://styles/YOUR_USERNAME/YOUR_STYLE_ID"

let gtiStyleURL: URL = {
    if !studioStyleURL.contains("YOUR_"), let url = URL(string: studioStyleURL) {
        return url
    }
    return Bundle.main.url(forResource: "gti-dark-style", withExtension: "json")!
}()

// Test route: Brno hlavní nádraží → Výstaviště Brno.
let testOrigin = CLLocationCoordinate2D(latitude: 49.1907, longitude: 16.6126)
let testDestination = CLLocationCoordinate2D(latitude: 49.1885, longitude: 16.5814)

// GTI colors
let gtiRed = UIColor(red: 232/255, green: 18/255, blue: 46/255, alpha: 1)      // #E8122E
let gtiAlternateGrey = UIColor(red: 90/255, green: 90/255, blue: 98/255, alpha: 1)   // #5A5A62

// Car model on the map. Make carScale bigger or smaller if the car looks wrong,
// and change carRotation (in degrees: 90, 180, 270) if it drives sideways or backwards.
let carScale: Double = 2.4
let carRotation: Double = 0

// MARK: - Map styles (same dark style day and night, like the GTI cluster)

// Both styles subclass NightStyle so the banners and buttons stay dark during the day too.
// DayStyle would paint them white.
final class GTIDayStyle: NightStyle {
    required init() {
        super.init()
        mapStyleURL = gtiStyleURL
        previewMapStyleURL = gtiStyleURL
        styleType = .day
    }

    override func apply() {
        super.apply()
        applyGTIAppearance()
    }
}

final class GTINightStyle: NightStyle {
    required init() {
        super.init()
        mapStyleURL = gtiStyleURL
        previewMapStyleURL = gtiStyleURL
        styleType = .night
    }

    override func apply() {
        super.apply()
        applyGTIAppearance()
    }
}

// The SDK calls apply() on the main thread, so it's safe to assume main-actor isolation here.
private func applyGTIAppearance() {
    MainActor.assumeIsolated {
        for idiom in [UIUserInterfaceIdiom.phone, .pad] {
            let traits = UITraitCollection(userInterfaceIdiom: idiom)

            // Alternative routes stay grey so red is reserved for the route you're driving.
            let mapView = NavigationMapView.appearance(for: traits)
            mapView.routeAlternateColor = gtiAlternateGrey
            mapView.routeAlternateCasingColor = UIColor(red: 10/255, green: 10/255, blue: 12/255, alpha: 1)

            // Remove the solid blue fills so the Liquid Glass behind them shows through.
            TopBannerView.appearance(for: traits).backgroundColor = .clear
            InstructionsBannerView.appearance(for: traits).backgroundColor = .clear
            LanesView.appearance(for: traits).backgroundColor = .clear
            NextBannerView.appearance(for: traits).backgroundColor = .clear
            BottomBannerView.appearance(for: traits).backgroundColor = .clear
            BottomPaddingView.appearance(for: traits).backgroundColor = .clear

            FloatingButton.appearance(for: traits).backgroundColor = .clear
            FloatingButton.appearance(for: traits).borderColor = .clear
            ResumeButton.appearance(for: traits).backgroundColor = .clear
            ResumeButton.appearance(for: traits).borderColor = .clear
            ResumeButton.appearance(for: traits).borderWidth = 0

            // "11 min" is coloured by traffic (green/orange/red) by default; keep it white like the rest.
            let timeLabel = TimeRemainingLabel.appearance(for: traits)
            timeLabel.normalTextColor = .white
            timeLabel.trafficUnknownColor = .white
            timeLabel.trafficLowColor = .white
            timeLabel.trafficModerateColor = .white
            timeLabel.trafficHeavyColor = .white
            timeLabel.trafficSevereColor = .white

            SeparatorView.appearance(for: traits).backgroundColor = UIColor.white.withAlphaComponent(0.12)
            // The bottom card is inset from the screen edges, so its full-width divider would stick out.
            SeparatorView.appearance(for: traits, whenContainedInInstancesOf: [BottomBannerView.self])
                .backgroundColor = .clear
        }
    }
}

// MARK: - Liquid Glass

// Liquid Glass on iOS 26 and later, a dark blur on older versions.
final class GTIGlassView: UIVisualEffectView {
    enum Shape {
        case capsule
        case rounded(CGFloat, CACornerMask)
    }

    static let allCorners: CACornerMask = [
        .layerMinXMinYCorner, .layerMaxXMinYCorner, .layerMinXMaxYCorner, .layerMaxXMaxYCorner
    ]

    private let shape: Shape

    init(shape: Shape) {
        self.shape = shape
        if #available(iOS 26.0, *) {
            let glass = UIGlassEffect(style: .regular)
            glass.tintColor = UIColor.black.withAlphaComponent(0.35)
            super.init(effect: glass)
        } else {
            super.init(effect: UIBlurEffect(style: .systemUltraThinMaterialDark))
            clipsToBounds = true
        }
        isUserInteractionEnabled = false
        translatesAutoresizingMaskIntoConstraints = false
        layer.cornerCurve = .continuous
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        switch shape {
        case .capsule:
            layer.cornerRadius = min(bounds.width, bounds.height) / 2
        case .rounded(let radius, let corners):
            layer.cornerRadius = radius
            layer.maskedCorners = corners
        }
    }
}

extension UIView {
    // Puts a glass layer behind this view's content. Safe to call more than once.
    func addGTIGlass(_ shape: GTIGlassView.Shape, insets: UIEdgeInsets = .zero) {
        guard !subviews.contains(where: { $0 is GTIGlassView }) else { return }
        let glass = GTIGlassView(shape: shape)
        insertSubview(glass, at: 0)
        NSLayoutConstraint.activate([
            glass.topAnchor.constraint(equalTo: topAnchor, constant: insets.top),
            glass.leadingAnchor.constraint(equalTo: leadingAnchor, constant: insets.left),
            glass.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -insets.right),
            glass.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -insets.bottom)
        ])
    }

    func allSubviews<T: UIView>(ofType type: T.Type) -> [T] {
        var found: [T] = []
        for subview in subviews {
            if let match = subview as? T { found.append(match) }
            found += subview.allSubviews(ofType: type)
        }
        return found
    }
}

// Same as the SDK's NavigationViewController, with glass behind the banners and buttons,
// 3D buildings and a camera that zooms and tilts more with the road ahead.
final class GTINavigationViewController: NavigationViewController {
    private var styleObservers = Set<AnyCancelable>()

    override func viewDidLoad() {
        super.viewDidLoad()
        guard let navigationMapView else { return }

        configureCamera(navigationMapView)

        // End navigation: a round glass ✕ under the route-overview button.
        let endButton: FloatingButton = .rounded(image: UIImage(
            systemName: "xmark",
            withConfiguration: UIImage.SymbolConfiguration(pointSize: 18, weight: .bold)
        ))
        endButton.accessibilityLabel = "End navigation"
        endButton.addTarget(self, action: #selector(endNavigationTapped), for: .touchUpInside)
        // The SDK's buttons are [route overview, mute, feedback]. Keep only route overview.
        floatingButtons = Array((floatingButtons ?? []).prefix(1)) + [endButton]
        showsReportFeedback = false

        // Styles can reload (for example day/night), so add the buildings every time one loads.
        guard let map = navigationMapView.mapView.mapboxMap else { return }
        map.onStyleLoaded.observe { [weak map] _ in
            if let map { add3DBuildings(to: map) }
        }.store(in: &styleObservers)
        if map.isStyleLoaded { add3DBuildings(to: map) }
    }

    @objc private func endNavigationTapped() {
        didTapCancel(self)
    }

    private func configureCamera(_ navigationMapView: NavigationMapView) {
        guard let dataSource = navigationMapView.navigationCamera.viewportDataSource as? MobileViewportDataSource
        else { return }
        var following = dataSource.options.followingCameraOptions
        // Driver's-eye view like Waze: always tilted, never flattening to top-down (SDK default: 45°).
        following.defaultPitch = 62
        // The SDK normally flattens the camera to top-down before each turn and zooms out to show the
        // road after it. Turn both off so the perspective stays steady through turns.
        following.pitchNearManeuver.enabled = false
        following.geometryFramingAfterManeuver.enabled = false
        // Zoom still follows the road ahead: closer through junctions and short streets, further out on
        // long straights, but always close enough to feel like driving (SDK default: 10.5...16.35).
        following.zoomRange = 15.6...18.2
        dataSource.options.followingCameraOptions = following
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)

        // Top banner: full width, hanging from the top of the screen with rounded bottom corners.
        navigationView.topBannerContainerView.addGTIGlass(
            .rounded(28, [.layerMinXMaxYCorner, .layerMaxXMaxYCorner])
        )

        // Bottom banner: a floating card above the home indicator.
        // (BottomPaddingView is a subclass of BottomBannerView, so match the exact type.)
        for banner in navigationView.bottomBannerContainerView.allSubviews(ofType: BottomBannerView.self)
            where type(of: banner) == BottomBannerView.self {
            banner.addGTIGlass(
                .rounded(28, GTIGlassView.allCorners),
                insets: UIEdgeInsets(top: 0, left: 8, bottom: 0, right: 8)
            )
        }

        // Round buttons on the map: route overview, end navigation, and Resume.
        for button in navigationView.allSubviews(ofType: FloatingButton.self) {
            button.addGTIGlass(.capsule)
        }
        for button in navigationView.allSubviews(ofType: ResumeButton.self) {
            button.addGTIGlass(.capsule)
        }
    }
}

// MARK: - Bottom bar

// Replaces the SDK's bottom bar: three even columns with the arrival time large in the middle.
//   [ 11 min ]   [ 20:37 ]   [ 5,2 km ]
//     time       ARRIVAL      distance
// Ending navigation is the ✕ button on the map, so the three columns stay perfectly even.
final class GTIBottomBanner: UIViewController, NavigationComponent {
    private let card = UIView()
    private let timeValue = UILabel()
    private let arrivalValue = UILabel()
    private let distanceValue = UILabel()

    private let durationFormatter: DateComponentsFormatter = {
        let formatter = DateComponentsFormatter()
        formatter.allowedUnits = [.hour, .minute]
        formatter.unitsStyle = .short
        return formatter
    }()

    private let arrivalFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        formatter.dateStyle = .none
        return formatter
    }()

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear

        card.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(card)
        card.addGTIGlass(.rounded(28, GTIGlassView.allCorners))

        let timeColumn = column(value: timeValue, caption: "time", size: 20)
        let arrivalColumn = column(value: arrivalValue, caption: "ARRIVAL", size: 34, emphasised: true)
        let distanceColumn = column(value: distanceValue, caption: "distance", size: 20)

        let columns = UIStackView(arrangedSubviews: [timeColumn, arrivalColumn, distanceColumn])
        columns.axis = .horizontal
        columns.distribution = .fillEqually
        columns.alignment = .center
        columns.translatesAutoresizingMaskIntoConstraints = false
        card.addSubview(columns)

        NSLayoutConstraint.activate([
            card.topAnchor.constraint(equalTo: view.topAnchor),
            card.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 8),
            card.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -8),
            card.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -4),
            card.heightAnchor.constraint(equalToConstant: 84),

            columns.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 12),
            columns.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -12),
            columns.centerYAnchor.constraint(equalTo: card.centerYAnchor),
        ])

        timeValue.text = "–"
        arrivalValue.text = "–"
        distanceValue.text = "–"
    }

    private func column(value: UILabel, caption: String, size: CGFloat, emphasised: Bool = false) -> UIStackView {
        value.font = emphasised
            ? .monospacedDigitSystemFont(ofSize: size, weight: .bold)
            : .monospacedDigitSystemFont(ofSize: size, weight: .semibold)
        value.textColor = .white
        value.textAlignment = .center
        value.adjustsFontSizeToFitWidth = true
        value.minimumScaleFactor = 0.6

        let captionLabel = UILabel()
        captionLabel.text = caption
        captionLabel.font = .systemFont(ofSize: 11, weight: emphasised ? .bold : .medium)
        captionLabel.textColor = emphasised ? gtiRed : UIColor.white.withAlphaComponent(0.55)
        captionLabel.textAlignment = .center

        let stack = UIStackView(arrangedSubviews: [value, captionLabel])
        stack.axis = .vertical
        stack.alignment = .center
        stack.spacing = emphasised ? 0 : 2
        return stack
    }

    // Called by the SDK on every location update.
    func onRouteProgressUpdated(_ progress: RouteProgress) {
        let remaining = progress.durationRemaining
        timeValue.text = remaining < 60 ? "<1 min" : durationFormatter.string(from: remaining)
        arrivalValue.text = arrivalFormatter.string(from: Date().addingTimeInterval(remaining))
        distanceValue.text = formattedDistance(progress.distanceRemaining)
    }

    private func formattedDistance(_ meters: CLLocationDistance) -> String {
        let formatter = MeasurementFormatter()
        formatter.unitOptions = .providedUnit
        formatter.numberFormatter.maximumFractionDigits = meters < 10_000 ? 1 : 0
        if meters < 1000 {
            formatter.numberFormatter.maximumFractionDigits = 0
            let rounded = (meters / 50).rounded() * 50
            return formatter.string(from: Measurement(value: rounded, unit: UnitLength.meters))
        }
        return formatter.string(from: Measurement(value: meters / 1000, unit: UnitLength.kilometers))
    }
}

// MARK: - 3D buildings

// Dark grey extruded buildings from Mapbox Streets, drawn above the roads and below the
// route line and labels so the red route always stays visible.
@MainActor
private func add3DBuildings(to map: MapboxMap) {
    let layerID = "gti-3d-buildings"
    guard map.sourceExists(withId: "composite"), !map.layerExists(withId: layerID) else { return }

    var layer = FillExtrusionLayer(id: layerID, source: "composite")
    layer.sourceLayer = "building"
    layer.minZoom = 14
    layer.filter = Exp(.eq) {
        Exp(.get) { "extrude" }
        "true"
    }
    layer.fillExtrusionColor = .constant(StyleColor(UIColor(red: 34/255, green: 34/255, blue: 40/255, alpha: 1)))
    layer.fillExtrusionHeight = .expression(Exp(.get) { "height" })
    layer.fillExtrusionBase = .expression(Exp(.get) { "min_height" })
    layer.fillExtrusionOpacity = .constant(0.9)

    let position: LayerPosition? = map.layerExists(withId: "road-major") ? .above("road-major") : nil
    try? map.addLayer(layer, layerPosition: position)
}

// MARK: - View controller

final class ViewController: UIViewController {

    // .simulation drives the route for you, so you can test at your desk.
    // Change to `.live` when you want to use real GPS in the car.
    private let navigationProvider = MapboxNavigationProvider(
        coreConfig: .init(locationSource: .simulation(initialLocation: CLLocation(
            latitude: testOrigin.latitude, longitude: testOrigin.longitude)))
    )
    private lazy var mapboxNavigation = navigationProvider.mapboxNavigation

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black

        let button = UIButton(type: .system)
        button.setTitle("Start test drive", for: .normal)
        button.setTitleColor(gtiRed, for: .normal)
        button.titleLabel?.font = .boldSystemFont(ofSize: 22)
        button.translatesAutoresizingMaskIntoConstraints = false
        button.addTarget(self, action: #selector(startNavigation), for: .touchUpInside)
        view.addSubview(button)
        NSLayoutConstraint.activate([
            button.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            button.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        ])
    }

    @objc private func startNavigation() {
        Task { await requestRouteAndNavigate() }
    }

    @MainActor
    private func requestRouteAndNavigate() async {
        let options = NavigationRouteOptions(coordinates: [testOrigin, testDestination])
        let request = mapboxNavigation.routingProvider().calculateRoutes(options: options)

        switch await request.result {
        case .failure(let error):
            print("Route request failed: \(error.localizedDescription)")
        case .success(let navigationRoutes):
            let bottomBanner = GTIBottomBanner()
            let navigationOptions = NavigationOptions(
                mapboxNavigation: mapboxNavigation,
                voiceController: navigationProvider.routeVoiceController,
                eventsManager: navigationProvider.eventsManager(),
                styles: [GTIDayStyle(), GTINightStyle()],
                bottomBanner: bottomBanner
            )
            let navigationViewController = GTINavigationViewController(
                navigationRoutes: navigationRoutes,
                navigationOptions: navigationOptions
            )
            navigationViewController.delegate = self
            navigationViewController.modalPresentationStyle = .fullScreen

            // After each progress update the SDK repaints the main route with a traffic
            // gradient, which would replace the red core. With traffic off, that gradient
            // is a single color: routeColor.
            if let navigationMapView = navigationViewController.navigationMapView {
                navigationMapView.showsTrafficOnRouteLine = false
                navigationMapView.routeColor = gtiRed

                // Your car instead of the arrow, if polo-gti.glb is in the GTINav folder.
                if let carURL = Bundle.main.url(forResource: "polo-gti", withExtension: "glb") {
                    navigationMapView.puckType = .puck3D(Puck3DConfiguration(
                        model: MapboxMaps.Model(uri: carURL),
                        modelScale: .constant([carScale, carScale, carScale]),
                        modelRotation: .constant([0, 0, carRotation])
                    ))
                }
            }

            present(navigationViewController, animated: true)
        }
    }
}

// MARK: - Glowing red route

// These delegate methods are also called for alternative routes. Returning nil there
// keeps the SDK's default (grey) layers, so only the main route glows.
private func isMainRoute(_ layerIdentifier: String) -> Bool {
    layerIdentifier.hasPrefix("com.mapbox.navigation.route_line.main")
}

extension ViewController: NavigationViewControllerDelegate {

    // Top layer: sharp, solid red core.
    func navigationViewController(
        _ navigationViewController: NavigationViewController,
        routeLineLayerWithIdentifier identifier: String,
        sourceIdentifier: String
    ) -> LineLayer? {
        guard isMainRoute(identifier) else { return nil }
        var layer = LineLayer(id: identifier, source: sourceIdentifier)
        layer.lineColor = .constant(StyleColor(gtiRed))
        layer.lineWidth = .constant(6)
        layer.lineOpacity = .constant(1.0)
        layer.lineCap = .constant(.round)
        layer.lineJoin = .constant(.round)
        return layer
    }

    // Bottom layer: wide, blurred, see-through red. This is the glow.
    func navigationViewController(
        _ navigationViewController: NavigationViewController,
        routeCasingLineLayerWithIdentifier identifier: String,
        sourceIdentifier: String
    ) -> LineLayer? {
        guard isMainRoute(identifier) else { return nil }
        var layer = LineLayer(id: identifier, source: sourceIdentifier)
        layer.lineColor = .constant(StyleColor(gtiRed))
        layer.lineWidth = .constant(22)
        layer.lineBlur = .constant(10)
        layer.lineOpacity = .constant(0.25)
        layer.lineCap = .constant(.round)
        layer.lineJoin = .constant(.round)
        return layer
    }

    func navigationViewControllerDidDismiss(
        _ navigationViewController: NavigationViewController,
        byCanceling canceled: Bool
    ) {
        dismiss(animated: true)
    }
}
