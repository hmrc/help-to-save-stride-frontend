;(function(window, document){
    const GOVUK = window.GOVUK;
    if (GOVUK) {
        const showHideContent = new GOVUK.ShowHideContent();
        showHideContent.init();
    }

    const checkbox = document.querySelector('[name="confirm_eligible"]')
    const button = document.querySelector('#continue')

    if (checkbox && button) {
        const enableButton = function() {
            button.removeAttribute('aria-disabled')
            button.removeAttribute('disabled')
        }
        const disableButton = function() {
            button.setAttribute('aria-disabled', 'true')
            button.setAttribute('disabled', 'disabled')
        }

        checkbox.addEventListener('change', function(e) {
            const isChecked = e.currentTarget.checked;
            if (isChecked) {
                enableButton()
            } else {
                disableButton()
            }
        })

        if (checkbox.checked) {
            enableButton()
        }
    }
})(window, document);
