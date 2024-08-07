/**
 * @class ModalAide
 * @classdesc A general-purpose helper class for creating and managing modal 
 * popups, particularly for displaying JSON data. This class facilitates the
 * creation of modal elements dynamically and provides methods to view JSON
 * content within the modal.
 */
export class ModalAide {
    /**
     * Constructs a new ModalAide instance.
     * @param {string} modalId - The unique identifier for the modal.
     */
    constructor(modalId) {
       // this.modalId = modalId;
       // this.modalContentClass = `${modalId}-content`;
       // this.modalClass = `${modalId}`;
       // this.closeClass = `${modalId}-close`;
       // this.jsonViewerId = `${modalId}-json`;
        this.fhirViewerId = 'fhir-viewer';
        this.fhirViewerModalId = 'fhir-viewer-modal';  
        this.fhirViewerModalContentClass = 'fhir-viewer-content';
        this.fhirViewerCloseClass = 'fhir-viewer-close';   
        
        this.modalId = 'json-viewer-modal';
        this.modalClass = 'json-viewer-modal';
        this.modalContentClass = 'json-viewer-modal-content';
        this.closeClass = 'json-viewer-modal-close';
        this.rowContainerClass = 'json-viewer-row-container';
        this.rowClass = 'json-viewer-row';
        this.jsonViewerId = 'json-viewer';
    }

    /**
     * Sets up the JSON viewer modal. This method assumes that the json-viewer
     * library is loaded. If the modal does not exist in the DOM, it creates
     * the necessary HTML and CSS for the modal.
     */
    setupJsonViewerModal() {
        // this method assume that https://github.com/alenaksu/json-viewer has been loaded
        // <script src="https://unpkg.com/@alenaksu/json-viewer@2.0.0/dist/json-viewer.bundle.js"></script>
        // TODO: automatically detect if json-viewer.bundle.js is missing and load it
        if (!document.getElementById(this.modalId)) {
            const style = document.createElement('style');
            style.id = this.modalId + "-styles";
            style.innerHTML = `
                .${this.modalClass} {
                    display: none;
                    position: fixed;
                    z-index: 1000;
                    left: 0;
                    top: 0;
                    width: 100%;
                    height: 100%;
                    overflow: auto;
                    background-color: rgba(0, 0, 0, 0.5);
                }

                .${this.modalContentClass} {
                    background-color: #fff;
                    margin: 15% auto;
                    padding: 20px;
                    border: 1px solid #888;
                    width: 80%;
                }

                .${this.closeClass} {
                    color: #aaa;
                    float: right;
                    font-size: 28px;
                    font-weight: bold;
                }

                .${this.closeClass}:hover,
                .${this.closeClass}:focus {
                    color: black;
                    text-decoration: none;
                    cursor: pointer;
                }
            `;
            document.head.appendChild(style);

            const modalHtml = `
                <div id="${this.modalId}" class="${this.modalClass}">
                    <div class="${this.modalContentClass}">
                        <span class="${this.closeClass}">&times;</span>
                        <json-viewer id="${this.jsonViewerId}"></json-viewer>
                    </div>
                </div>
            `;
            document.body.insertAdjacentHTML('beforeend', modalHtml);

            document.querySelector(`.${this.closeClass}`).onclick = () => {
                document.getElementById(this.modalId).style.display = 'none';
            };

            window.onclick = (event) => {
                if (event.target == document.getElementById(this.modalId)) {
                    document.getElementById(this.modalId).style.display = 'none';
                }
            };
        }
    }
   
    /**
     * Displays a JSON value within the modal.
     * @param {Object} value - The JSON object to be displayed in the modal.
     */
    viewJsonValue(value) {
        this.setupJsonViewerModal();
        document.querySelector(`#${this.jsonViewerId}`).data = value;
        document.getElementById(this.modalId).style.display = 'block';
    }

    /**
     * Fetches JSON data from a specified URL and displays it within the modal.
     * @param {string} url - The URL to fetch JSON data from.
     */
    viewFetchedJsonValue(url) {
        fetch(url)
            .then(response => response.json())
            .then(data => this.viewJsonValue(data))
            .catch(error => console.error(`Error fetching data from ${url}`, error));
    }

    /**
     * Sets up the FHIR viewer modal. This method creates the necessary HTML 
     * and CSS for the FHIR viewer modal if it does not exist in the DOM.
     */
    setupFhirViewerModal() {
        if (!document.getElementById(this.fhirViewerModalId)) {
            const style = document.createElement('style');
            style.id = `${this.fhirViewerModalId}-styles`;
            style.innerHTML = `
                .${this.fhirViewerModalId} {
                        display: none;
                        position: fixed;
                        z-index: 1000;
                        left: 0;
                        top: 0;
                        width: 100%;
                        height: 100%;
                        overflow: auto;
                        background-color: rgba(0, 0, 0, 0.5);
                    }

                    .${this.fhirViewerModalContentClass} {
                        background-color: #fff;
                        margin: 15% auto;
                        padding: 20px;
                        border: 1px solid #888;
                        width: 80%;
                    }

                    .${this.fhirViewerCloseClass} {
                        color: #aaa;
                        float: right;
                        font-size: 28px;
                        font-weight: bold;
                        cursor: pointer;
                    }

                    .${this.fhirViewerCloseClass}:hover,
                    .${this.fhirViewerCloseClass}:focus {
                        color: black;
                        text-decoration: none;
                        cursor: pointer;
                    }
                `;
                document.head.appendChild(style);

                const modalHtml = `
                    <div id="${this.fhirViewerModalId}" class="${this.fhirViewerModalId}">
                        <div class="${this.fhirViewerModalContentClass}">
                            <span id="fhir-viewer-close" class="${this.fhirViewerCloseClass}">&times;</span>
                            <fhir-viewer id="${this.fhirViewerId}"></fhir-viewer>
                        </div>
                    </div>
                `;
                document.body.insertAdjacentHTML('beforeend', modalHtml);

                // Add event listeners for closing the modal
                document.getElementById('fhir-viewer-close').addEventListener('click', this.closeFhirViewer.bind(this));
                window.addEventListener('click', (event) => {
                    if (event.target.id === this.fhirViewerModalId) {
                        this.closeFhirViewer();
                    }
                });
            }
    }

    /**
     * Shows the FHIR viewer modal with the specified URL.
     * @param {string} fhirUrl - The URL to be displayed in the FHIR viewer.
     */
    showFhirViewer(fhirUrl) {
        this.setupFhirViewerModal();
        const fhirViewer = document.getElementById(this.fhirViewerId);
        fhirViewer.setAttribute('src', fhirUrl);
        document.getElementById(this.fhirViewerModalId).style.display = 'block';
    }

    /**
     * Closes the FHIR viewer modal.
     */
    closeFhirViewer() {
        const modal = document.getElementById(this.fhirViewerModalId);
        if (modal) {
            modal.style.display = 'none';
        }
    }
    
    
    setupJsonViewerModalCustom() {
        // Assuming https://github.com/alenaksu/json-viewer is already loaded
        if (!document.getElementById(this.modalId)) {
            const style = document.createElement('style');
            style.id = this.modalId + "-styles";
            style.innerHTML = `
                .${this.modalClass} {
                    display: none;
                    position: fixed;
                    z-index: 1000;
                    left: 0;
                    top: 0;
                    width: 100%;
                    height: 100%;
                    overflow: auto;
                    background-color: rgba(0, 0, 0, 0.5);
                }
    
                .${this.modalContentClass} {
                    background-color: #fff;
                    margin: 15% auto;
                    padding: 20px;
                    border: 1px solid #888;
                    width: 80%;
                }
    
                .${this.closeClass} {
                    color: #aaa;
                    float: right;
                    font-size: 28px;
                    font-weight: bold;
                }
    
                .${this.closeClass}:hover,
                .${this.closeClass}:focus {
                    color: black;
                    text-decoration: none;
                    cursor: pointer;
                }
    
                .${this.rowClass} {
                    padding: 10px;
                }
    
                .${this.rowClass}:nth-child(even) {
                    background-color: #f9f9f9;
                }
    
                .${this.rowClass}:nth-child(odd) {
                    background-color: #fff;
                }
                .resource {
                    margin-bottom: 1rem;
                    font-family: Arial, sans-serif;
                }
                .header {
                    font-size: 1.25rem;
                    font-weight: bold;
                    color: #333;
                    margin-bottom: 0.5rem;
                }

                .table th {
                    background-color: #f2f2f2;
                    font-weight: normal;
                    color: #555;
                }

                .table th, .table td {
                    padding: 0.75rem;
                    text-align: left;
                    border-bottom: 1px solid #ddd;
                }
            `;
            document.head.appendChild(style);
    
            const modalHtml = `
                <div id="${this.modalId}" class="${this.modalClass}">
                    <div class="${this.modalContentClass}">
                        <span class="${this.closeClass}">&times;</span> 
                        <div id="${this.jsonViewerId}" class="${this.rowContainerClass}"></div>
                    </div>
                </div>
            `;
            document.body.insertAdjacentHTML('beforeend', modalHtml);
    
            document.querySelector(`.${this.closeClass}`).onclick = () => {
                document.getElementById(this.modalId).style.display = 'none';
            };
    
            window.onclick = (event) => {
                if (event.target == document.getElementById(this.modalId)) {
                    document.getElementById(this.modalId).style.display = 'none';
                }
            };
        }
    }
    
    /**
     * Displays a JSON value within the modal.
     * @param {Object} value - The JSON object to be displayed in the modal.
     */
    viewJsonValueCustom(value) {
        this.setupJsonViewerModalCustom();
        const container = document.querySelector(`#${this.jsonViewerId}`);
        container.innerHTML = this.convertJsonToHtmlRowsCustom(value);
        document.getElementById(this.modalId).style.display = 'block';
    }
    
    /**
     * Converts a JSON object into HTML rows.
     * @param {Object} json - The JSON object to convert.
     * @return {string} The HTML string of rows.
     */
    convertJsonToHtmlRowsCustom(json) {
        let html = '<div class="resource observation"><div class="header">Interaction Details</div><table class="table"><tbody>';
    
        function parseObject(obj) {
            for (const [key, value] of Object.entries(obj)) {
                if (typeof value === 'object' && value !== null) {
                    html += `<tr><th>${key}</th><td>`;
                    parseObject(value);
                    html += `</td></tr>`;
                } else {
                    html += `<tr><th>${key}</th><td>${value}</td></tr>`;
                }
            }
        }
    
        parseObject(json);
        html += '</tbody></table></div>';
        return html;
    }
    
    
    
    
    /**
     * Fetches JSON data from a specified URL and displays it within the modal.
     * @param {string} url - The URL to fetch JSON data from.
     */
    viewFetchedJsonValueCustom(url) {
        fetch(url)
            .then(response => response.json())
            .then(data => this.viewJsonValueCustom(data))
            .catch(error => console.error(`Error fetching data from ${url}`, error));
    }
    
   
     
    
}

export default ModalAide;
